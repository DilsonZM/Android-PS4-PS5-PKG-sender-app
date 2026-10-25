package com.remotesend.app.data.install

import com.remotesend.app.core.AppLogger
import com.remotesend.app.data.network.ConsoleClient
import com.remotesend.app.data.network.ConsoleClientFactory
import com.remotesend.app.data.network.ConsoleDetector
import com.remotesend.app.data.persist.StateStore
import com.remotesend.app.data.repository.PackageRepository
import com.remotesend.app.data.repository.QueueRepository
import com.remotesend.app.data.server.ServerManager
import com.remotesend.app.data.service.NotificationHelper
import com.remotesend.app.domain.model.ConsoleInstallResult
import com.remotesend.app.domain.model.ConsoleType
import com.remotesend.app.domain.model.InstallRuntime
import com.remotesend.app.domain.model.PkgFile
import com.remotesend.app.domain.model.QueueItem
import com.remotesend.app.domain.model.QueueStatus
import com.remotesend.app.domain.model.StartResult
import com.remotesend.app.domain.model.TaskProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class InstallationManager(
    private val stateStore: StateStore,
    private val packages: PackageRepository,
    private val queue: QueueRepository,
    private val serverManager: ServerManager,
    private val detector: ConsoleDetector,
    private val clientFactory: ConsoleClientFactory,
    private val notifier: NotificationHelper,
    private val logger: AppLogger,
    private val scope: CoroutineScope,
) {
    private val _runtime = MutableStateFlow<InstallRuntime>(InstallRuntime.Idle)
    val runtime: StateFlow<InstallRuntime> = _runtime.asStateFlow()

    private val busy = AtomicBoolean(false)
    private var job: Job? = null

    @Volatile
    private var currentItemId: String? = null

    val isBusy: Boolean
        get() = busy.get()

    suspend fun start(itemId: String): StartResult {
        val snapshot = stateStore.snapshot()
        val item = snapshot.queue.firstOrNull { it.id == itemId }
            ?: return StartResult.Rejected("El elemento ya no está en la cola")
        val pkg = snapshot.packages.firstOrNull { it.id == item.pkgId }
            ?: return StartResult.Rejected("El PKG ya no está disponible en la biblioteca")

        if (!serverManager.isRunning) {
            return StartResult.Rejected("Inicia el servidor local antes de instalar")
        }
        if (serverManager.packageUrl(pkg) == null) {
            return StartResult.Rejected("No se pudo generar la URL del PKG. Reinicia el servidor.")
        }
        if (snapshot.console.host.isBlank()) {
            return StartResult.Rejected("Configura la IP de la consola primero")
        }
        if (!busy.compareAndSet(false, true)) {
            return StartResult.Rejected("Ya hay una instalación en curso")
        }

        currentItemId = itemId
        _runtime.value = InstallRuntime.Working(
            queueItemId = itemId,
            title = item.effectiveTitle,
            status = QueueStatus.INSTALLING,
            progress = item.progress,
            restSeconds = 0,
        )

        job = scope.launch {
            try {
                runInstall(itemId)
            } catch (cancellation: CancellationException) {
                logger.info("Instalación", "Instalación cancelada")
                throw cancellation
            } catch (exception: Exception) {
                fail(itemId, exception.message ?: "Error inesperado")
            } finally {
                currentItemId = null
                busy.set(false)
                _runtime.value = InstallRuntime.Idle
                notifier.cancelInstallProgress()
            }
        }

        return StartResult.Started
    }

    private suspend fun runInstall(itemId: String) {
        val snapshot = stateStore.snapshot()
        val item = snapshot.queue.firstOrNull { it.id == itemId } ?: return
        val pkg = snapshot.packages.firstOrNull { it.id == item.pkgId }
            ?: run {
                fail(itemId, "El PKG no está disponible")
                return
            }
        val config = snapshot.console
        val pkgUrl = serverManager.packageUrl(pkg) ?: run {
            fail(itemId, "El servidor local se detuvo")
            return
        }
        val iconUrl = serverManager.iconUrl(pkg)

        logger.info("Instalación", "Buscando consola en ${config.host}")
        val detection = detector.detect(config)
        if (!detection.reachable) {
            fail(itemId, detection.message)
            return
        }

        val resolvedType = if (config.type == ConsoleType.AUTO) detection.type else config.type
        if (resolvedType == ConsoleType.AUTO) {
            fail(itemId, "No se pudo determinar el tipo de consola")
            return
        }

        val client = clientFactory.create(resolvedType, config)
        queue.update(itemId) {
            it.copy(consoleType = resolvedType, serverUrl = pkgUrl)
        }
        logger.success(
            "Instalación",
            "Consola ${resolvedType.name} detectada (${detection.latencyMs ?: 0} ms)",
        )

        val latestItem = queue.find(itemId) ?: return
        if (latestItem.status == QueueStatus.STOPPED) {
            return
        }

        val existingTask = item.taskId
        val isResume = existingTask != null &&
            (
                item.status == QueueStatus.PAUSED ||
                    item.status == QueueStatus.STOPPED ||
                    item.status == QueueStatus.INSTALLING
                ) &&
            resolvedType == ConsoleType.PS4_RPI

        if (isResume) {
            if (client.resume(existingTask)) {
                queue.update(itemId) {
                    it.copy(
                        status = QueueStatus.INSTALLING,
                        message = null,
                        startedAt = it.startedAt ?: System.currentTimeMillis(),
                    )
                }
                val updated = queue.find(itemId) ?: return
                notifyStartedIfEnabled(updated)
                _runtime.value = InstallRuntime.Working(
                    itemId,
                    updated.effectiveTitle,
                    QueueStatus.INSTALLING,
                    0,
                    0,
                )
                pollTask(client, itemId, existingTask, pkg, config, resolvedType)
                return
            }
            logger.warn("Instalación", "No se pudo reanudar la tarea $existingTask; se reinstalará")
        }

        val result = client.install(pkg, pkgUrl, iconUrl)
        val afterRequest = queue.find(itemId) ?: return
        if (afterRequest.status == QueueStatus.STOPPED) {
            logger.info("Instalación", "Solicitud completada pero el elemento fue detenido")
            return
        }

        when (result) {
            is ConsoleInstallResult.Accepted -> {
                val sentDirectly = resolvedType == ConsoleType.PS5_ETAHEN ||
                    resolvedType == ConsoleType.PS4_GOLDHEN
                val status = if (sentDirectly) QueueStatus.SENT else QueueStatus.INSTALLING

                queue.markStarted(itemId, status, result.taskId, resolvedType, pkgUrl)
                val updated = queue.find(itemId) ?: return
                notifyStartedIfEnabled(updated)
                _runtime.value = InstallRuntime.Working(
                    itemId,
                    updated.effectiveTitle,
                    status,
                    0,
                    0,
                )

                when {
                    sentDirectly -> onCompleted(itemId)

                    result.taskId != null -> pollTask(
                        client = client,
                        itemId = itemId,
                        taskId = result.taskId,
                        pkg = pkg,
                        config = config,
                        type = resolvedType,
                    )

                    else -> {
                        queue.markFinished(
                            itemId,
                            QueueStatus.SENT,
                            "Solicitud aceptada sin task id",
                            100,
                        )
                        onCompleted(itemId)
                    }
                }
            }

            is ConsoleInstallResult.AlreadyInstalled -> {
                queue.markFinished(itemId, QueueStatus.EXISTS, result.message, 100)
                onCompleted(itemId)
            }

            is ConsoleInstallResult.Failure -> {
                fail(itemId, result.message, result.code)
            }
        }
    }

    private suspend fun pollTask(
        client: ConsoleClient,
        itemId: String,
        taskId: Long,
        pkg: PkgFile,
        config: com.remotesend.app.domain.model.ConsoleConfig,
        type: ConsoleType,
    ) {
        var consecutiveFailures = 0
        var lastNotifiedProgress = -1
        var lastLoggedProgress = -10
        var lastTransferredBytes = 0L
        var lastSampleAt = 0L

        while (currentCoroutineContext().isActive) {
            val interval = config.pollIntervalMs.coerceIn(1_000L, 10_000L)
            delay(interval)

            val current = queue.find(itemId) ?: return
            if (current.status == QueueStatus.STOPPED) return
            if (!serverManager.isRunning) {
                fail(itemId, "El servidor local se detuvo durante la instalación")
                return
            }
            if (current.status == QueueStatus.PAUSED) continue

            when (val progress = client.taskProgress(taskId)) {
                is TaskProgress.Running -> {
                    consecutiveFailures = 0
                    val now = System.currentTimeMillis()
                    var speed = 0L
                    if (progress.transferredBytes > 0L) {
                        if (lastSampleAt > 0L &&
                            progress.transferredBytes >= lastTransferredBytes
                        ) {
                            val deltaBytes = progress.transferredBytes - lastTransferredBytes
                            val deltaMs = now - lastSampleAt
                            if (deltaBytes > 0L && deltaMs > 0L) {
                                speed = deltaBytes * 1_000L / deltaMs
                            }
                        }
                        lastTransferredBytes = progress.transferredBytes
                        lastSampleAt = now
                    }
                    queue.setProgress(
                        id = itemId,
                        progress = progress.percent,
                        restSeconds = progress.restSeconds,
                        transferredBytes = progress.transferredBytes,
                        totalBytes = progress.totalBytes,
                        speedBytesPerSecond = speed,
                    )
                    val updated = queue.find(itemId) ?: return
                    _runtime.value = InstallRuntime.Working(
                        queueItemId = itemId,
                        title = updated.effectiveTitle,
                        status = QueueStatus.INSTALLING,
                        progress = progress.percent,
                        restSeconds = progress.restSeconds,
                    )
                    if (progress.percent - lastNotifiedProgress >= 2 || lastNotifiedProgress < 0) {
                        if (notificationsAllowed()) notifier.showInstallProgress(updated)
                        lastNotifiedProgress = progress.percent
                    }
                    if (progress.percent >= lastLoggedProgress + 10) {
                        logger.debug(
                            "Instalación",
                            "${pkg.effectiveTitle}: ${progress.percent}%",
                            progress.detail,
                        )
                        lastLoggedProgress = progress.percent
                    }
                }

                TaskProgress.Completed -> {
                    queue.markFinished(itemId, QueueStatus.COMPLETED, null, 100)
                    onCompleted(itemId)
                    return
                }

                is TaskProgress.TaskMissing -> {
                    queue.markFinished(itemId, QueueStatus.COMPLETED, progress.message, 100)
                    onCompleted(itemId)
                    return
                }

                is TaskProgress.Failure -> {
                    consecutiveFailures++
                    logger.warn(
                        "Instalación",
                        "Error consultando la tarea ($consecutiveFailures/3)",
                        progress.message,
                    )
                    if (consecutiveFailures >= 3) {
                        fail(itemId, progress.message, progress.code)
                        return
                    }
                    delay(1_000L)
                }

                TaskProgress.Unsupported -> {
                    queue.markFinished(itemId, QueueStatus.SENT, "Progreso no disponible", 100)
                    onCompleted(itemId)
                    return
                }
            }
        }
    }

    private suspend fun onCompleted(itemId: String) {
        val updated = queue.find(itemId) ?: return
        notifier.cancelInstallProgress()
        if (notificationsAllowed()) {
            when (updated.status) {
                QueueStatus.SENT -> notifier.notifySent(updated)
                QueueStatus.EXISTS -> notifier.notifyCompleted(updated)
                QueueStatus.COMPLETED -> notifier.notifyCompleted(updated)
                else -> Unit
            }
        }

        val autoAdvance = stateStore.snapshot().server.autoAdvance
        if (!autoAdvance) return

        scope.launch {
            delay(1_200L)
            val next = queue.nextQueued(excludeId = itemId) ?: return@launch
            val result = start(next.id)
            if (result is StartResult.Rejected) {
                logger.debug("Cola", "No se pudo auto-iniciar ${next.effectiveTitle}: ${result.message}")
            }
        }
    }

    private suspend fun fail(itemId: String, message: String, code: Long? = null) {
        queue.markFinished(itemId, QueueStatus.FAILED, message)
        val updated = queue.find(itemId)
        if (updated != null && notificationsAllowed()) notifier.notifyFailed(updated, message)
        logger.error("Instalación", message, code?.let { "Código: $it" })
        notifier.cancelInstallProgress()
    }

    suspend fun pauseActive(): String? {
        val active = queue.snapshot().firstOrNull {
            it.status == QueueStatus.INSTALLING
        } ?: return "No hay una instalación activa"
        val taskId = active.taskId ?: return "La tarea no tiene un id de seguimiento"
        val type = active.consoleType ?: ConsoleType.PS4_RPI
        if (type != ConsoleType.PS4_RPI) {
            return "Pausar solo está disponible con flatZ RPI (PS4)"
        }
        val config = stateStore.snapshot().console
        val client = clientFactory.create(type, config)
        return if (client.pause(taskId)) {
            queue.update(active.id) { it.copy(status = QueueStatus.PAUSED) }
            val updated = queue.find(active.id)
            if (updated != null) {
                _runtime.value = InstallRuntime.Working(
                    active.id,
                    updated.effectiveTitle,
                    QueueStatus.PAUSED,
                    updated.progress,
                    updated.restSeconds,
                )
            }
            logger.info("Instalación", "Instalación pausada: ${active.effectiveTitle}")
            null
        } else {
            "La consola rechazó la pausa"
        }
    }

    suspend fun resumeActive(): StartResult {
        val paused = queue.snapshot().firstOrNull {
            it.status == QueueStatus.PAUSED || it.status == QueueStatus.STOPPED
        } ?: return StartResult.Rejected("No hay una instalación pausada o detenida")

        if (busy.get()) {
            if (currentItemId != paused.id) {
                return StartResult.Rejected("Ya hay una instalación en curso")
            }
            val taskId = paused.taskId
                ?: return StartResult.Rejected("La tarea no tiene un id de seguimiento")
            val type = paused.consoleType ?: ConsoleType.PS4_RPI
            if (type != ConsoleType.PS4_RPI) {
                return StartResult.Rejected("Reanudar solo está disponible con flatZ RPI (PS4)")
            }
            val config = stateStore.snapshot().console
            val client = clientFactory.create(type, config)
            if (!client.resume(taskId)) {
                return StartResult.Rejected("La consola rechazó la reanudación")
            }
            queue.update(paused.id) { it.copy(status = QueueStatus.INSTALLING, message = null) }
            return StartResult.Started
        }

        return start(paused.id)
    }

    suspend fun stopActive(): String? {
        val active = queue.snapshot().firstOrNull {
            it.status == QueueStatus.INSTALLING || it.status == QueueStatus.PAUSED
        } ?: return "No hay una instalación activa"

        val taskId = active.taskId
        val type = active.consoleType
        if (taskId != null && type == ConsoleType.PS4_RPI) {
            val config = stateStore.snapshot().console
            runCatching { clientFactory.create(type, config).stop(taskId) }
        }

        queue.update(active.id) {
            it.copy(
                status = QueueStatus.STOPPED,
                message = "Detenido por el usuario",
                finishedAt = System.currentTimeMillis(),
            )
        }
        job?.cancel()
        notifier.cancelInstallProgress()
        logger.info("Instalación", "Instalación detenida: ${active.effectiveTitle}")
        return null
    }

    suspend fun removeItem(itemId: String) {
        val item = queue.find(itemId) ?: return
        if (currentItemId == itemId) {
            stopActive()
        }

        val taskId = item.taskId
        val type = item.consoleType
        if (taskId != null && type == ConsoleType.PS4_RPI) {
            val config = stateStore.snapshot().console
            runCatching { clientFactory.create(type, config).unregister(taskId) }
        }
        queue.remove(itemId)
    }

    private suspend fun notificationsAllowed(): Boolean =
        stateStore.snapshot().settings.notificationsEnabled

    private suspend fun notifyStartedIfEnabled(item: QueueItem) {
        if (notificationsAllowed()) notifier.notifyStarted(item)
    }

    suspend fun resetQueue(): String? {
        if (busy.get()) stopActive()
        queue.reset()
        notifier.cancelInstallProgress()
        return null
    }
}
