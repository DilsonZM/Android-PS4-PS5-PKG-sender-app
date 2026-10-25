package com.remotesend.app.data.server

import android.content.Context
import com.remotesend.app.core.AppLogger
import com.remotesend.app.core.NetworkUtils
import com.remotesend.app.data.persist.StateStore
import com.remotesend.app.data.repository.PackageRepository
import com.remotesend.app.domain.model.PkgFile
import com.remotesend.app.domain.model.ServerRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream

class ServerManager(
    private val context: Context,
    private val stateStore: StateStore,
    private val packageRepository: PackageRepository,
    private val logger: AppLogger,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ServerRuntime>(ServerRuntime.Stopped)
    val state: StateFlow<ServerRuntime> = _state.asStateFlow()

    private var server: PkgHttpServer? = null

    val isRunning: Boolean
        get() = _state.value is ServerRuntime.Running

    suspend fun start(): ServerRuntime = withContext(Dispatchers.IO) {
        val current = _state.value
        if (current is ServerRuntime.Running) return@withContext current

        _state.value = ServerRuntime.Starting
        val config = stateStore.snapshot().server
        if (config.port !in 1..65535) {
            val error = ServerRuntime.Error("Puerto de servidor inválido: ${config.port}")
            _state.value = error
            return@withContext error
        }

        val addresses = NetworkUtils.localAddresses()
        val host = when {
            config.bindIp.isNotBlank() -> addresses.firstOrNull { it.ip == config.bindIp }?.ip
            else -> NetworkUtils.defaultIpv4()
        }

        if (host == null) {
            val message = if (config.bindIp.isNotBlank()) {
                "La IP ${config.bindIp} ya no está disponible. Revisa la configuración de red."
            } else {
                "No hay una IP local disponible. Conéctate a la misma red Wi-Fi que la consola."
            }
            logger.error("Servidor", message)
            val error = ServerRuntime.Error(message)
            _state.value = error
            return@withContext error
        }

        val provider = AndroidHttpContentProvider(packageRepository)
        val newServer = PkgHttpServer(
            port = config.port,
            provider = provider,
            logger = logger,
            scope = scope,
        )

        try {
            val boundPort = newServer.start()
            server = newServer
            val running = ServerRuntime.Running(
                host = host,
                port = boundPort,
                startedAt = System.currentTimeMillis(),
            )
            _state.value = running
            logger.success(
                "Servidor",
                "Servidor HTTP activo en http://$host:$boundPort",
            )
            running
        } catch (exception: Exception) {
            runCatching { newServer.stop() }
            server = null
            val message = when {
                exception.message?.contains("in use", ignoreCase = true) == true ->
                    "El puerto ${config.port} está ocupado. Elige otro puerto."

                else -> "No se pudo iniciar el servidor: ${exception.message}"
            }
            logger.error("Servidor", message, exception.stackTraceToString())
            val error = ServerRuntime.Error(message)
            _state.value = error
            error
        }
    }

    fun stop() {
        val running = _state.value as? ServerRuntime.Running
        runCatching { server?.stop() }
        server = null
        _state.value = ServerRuntime.Stopped
        if (running != null) {
            logger.info("Servidor", "Servidor HTTP detenido")
        }
    }

    fun packageUrl(pkg: PkgFile): String? {
        val running = _state.value as? ServerRuntime.Running ?: return null
        return "${running.baseUrl}/pkg/${pkg.id}/${pkg.safeName}"
    }

    fun iconUrl(pkg: PkgFile): String? {
        if (pkg.iconPath == null) return null
        val running = _state.value as? ServerRuntime.Running ?: return null
        return "${running.baseUrl}/icon/${pkg.id}"
    }

    private class AndroidHttpContentProvider(
        private val repository: PackageRepository,
    ) : HttpContentProvider {
        override suspend fun packages(): List<ServerPackage> {
            return repository.snapshot().map { pkg ->
                ServerPackage(
                    id = pkg.id,
                    safeName = pkg.safeName,
                    displayName = pkg.displayName,
                    title = pkg.effectiveTitle,
                    titleId = pkg.titleId,
                    sizeBytes = pkg.sizeBytes,
                    hasIcon = pkg.iconPath != null,
                )
            }
        }

        override suspend fun openStream(packageId: String, offset: Long): InputStream? {
            val pkg = repository.find(packageId) ?: return null
            return repository.openStreamAt(pkg, offset)
        }

        override suspend fun icon(packageId: String): ByteArray? {
            val pkg = repository.find(packageId) ?: return null
            return repository.readIcon(pkg)
        }
    }
}
