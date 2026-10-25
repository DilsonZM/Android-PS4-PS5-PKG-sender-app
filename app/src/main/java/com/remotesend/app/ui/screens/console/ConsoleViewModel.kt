package com.remotesend.app.ui.screens.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotesend.app.core.NetworkUtils
import com.remotesend.app.data.service.RemoteSendService
import com.remotesend.app.di.AppContainer
import com.remotesend.app.domain.model.ConsoleConfig
import com.remotesend.app.domain.model.ConsoleConnectionState
import com.remotesend.app.domain.model.ConsoleConnectionStatus
import com.remotesend.app.domain.model.ConsoleType
import com.remotesend.app.domain.model.LanCandidate
import com.remotesend.app.domain.model.NetworkAddress
import com.remotesend.app.domain.model.ScanProgress
import com.remotesend.app.domain.model.ServerConfig
import com.remotesend.app.domain.model.ServerRuntime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConsoleForm(
    val type: ConsoleType = ConsoleType.AUTO,
    val host: String = "",
    val httpPort: String = "12800",
    val tcpPort: String = "9090",
    val timeoutMs: String = "3500",
    val pollIntervalMs: String = "2200",
    val preferPs5ForTcp: Boolean = true,
    val bindIp: String = "",
    val serverPort: String = "8337",
    val autoStart: Boolean = true,
    val autoAdvance: Boolean = true,
    val scanSubdirectories: Boolean = false,
    val readMetadata: Boolean = true,
    val autoScanOnStartup: Boolean = true,
    val scanPath: String = "/storage/emulated/0/Download",
)

data class ConsoleUiState(
    val form: ConsoleForm = ConsoleForm(),
    val loaded: Boolean = false,
    val serverRuntime: ServerRuntime = ServerRuntime.Stopped,
    val addresses: List<NetworkAddress> = emptyList(),
    val connection: ConsoleConnectionState = ConsoleConnectionState(),
    val saving: Boolean = false,
    val scanning: Boolean = false,
    val scanScanned: Int = 0,
    val scanTotal: Int = 0,
    val candidates: List<LanCandidate> = emptyList(),
) {
    val checking: Boolean
        get() = connection.status == ConsoleConnectionStatus.CHECKING
}

private data class ConsoleLocalState(
    val form: ConsoleForm = ConsoleForm(),
    val loaded: Boolean = false,
    val addresses: List<NetworkAddress> = emptyList(),
    val saving: Boolean = false,
    val scanning: Boolean = false,
    val scanScanned: Int = 0,
    val scanTotal: Int = 0,
    val candidates: List<LanCandidate> = emptyList(),
)

class ConsoleViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val local = MutableStateFlow(ConsoleLocalState())
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()
    private var scanJob: Job? = null

    val uiState: StateFlow<ConsoleUiState> = combine(
        local,
        container.serverManager.state,
        container.consoleDetector.connection,
    ) { localState, server, connection ->
        ConsoleUiState(
            form = localState.form,
            loaded = localState.loaded,
            serverRuntime = server,
            addresses = localState.addresses,
            connection = connection,
            saving = localState.saving,
            scanning = localState.scanning,
            scanScanned = localState.scanScanned,
            scanTotal = localState.scanTotal,
            candidates = localState.candidates,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConsoleUiState(),
    )

    init {
        viewModelScope.launch {
            val state = container.stateStore.snapshot()
            local.update {
                it.copy(
                    form = formFrom(state.console, state.server),
                    addresses = NetworkUtils.localAddresses(),
                    loaded = true,
                )
            }
        }
    }

    fun updateForm(transform: (ConsoleForm) -> ConsoleForm) {
        local.update { it.copy(form = transform(it.form)) }
    }

    fun refreshAddresses() {
        local.update {
            it.copy(
                addresses = NetworkUtils.localAddresses(),
                form = if (it.form.bindIp.isBlank()) {
                    it.form.copy(bindIp = NetworkUtils.defaultIpv4() ?: "")
                } else {
                    it.form
                },
            )
        }
    }

    fun save() {
        val form = local.value.form
        val error = validate(form)
        if (error != null) {
            emit(error)
            return
        }

        viewModelScope.launch {
            local.update { it.copy(saving = true) }
            try {
                val old = container.stateStore.snapshot()
                val console = buildConsoleConfig(form)
                val server = buildServerConfig(form)
                val needsRestart = container.serverManager.isRunning &&
                    (old.server.port != server.port || old.server.bindIp != server.bindIp)
                val hasActiveInstall = old.queue.any {
                    it.status == com.remotesend.app.domain.model.QueueStatus.INSTALLING ||
                        it.status == com.remotesend.app.domain.model.QueueStatus.PAUSED
                }

                container.settingsRepository.saveConsole(console)
                container.settingsRepository.saveServer(server)
                emit("Configuración guardada")

                if (needsRestart && hasActiveInstall) {
                    emit("Hay una instalación activa: el nuevo puerto se aplicará al reiniciar el servidor")
                } else if (needsRestart) {
                    emit("Reiniciando servidor con la nueva configuración…")
                    RemoteSendService.stopServer(container.appContext)
                    delay(900L)
                    RemoteSendService.startServer(container.appContext)
                }
            } catch (exception: Exception) {
                emit("No se pudo guardar: ${exception.message}")
            } finally {
                local.update { it.copy(saving = false) }
            }
        }
    }

    fun checkConnection() {
        val form = local.value.form
        val error = validateConnection(form)
        if (error != null) {
            emit(error)
            return
        }
        if (local.value.loaded.not() ||
            container.consoleDetector.connection.value.status == ConsoleConnectionStatus.CHECKING
        ) {
            return
        }

        viewModelScope.launch {
            val result = container.consoleDetector.detect(buildConsoleConfig(form))
            emit(result.message)
        }
    }

    fun scanNetwork() {
        if (local.value.scanning) return
        val localIp = local.value.form.bindIp.ifBlank { NetworkUtils.defaultIpv4() }
        if (localIp == null) {
            emit("No hay una red local disponible. Conéctate por Wi-Fi.")
            return
        }

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            local.update {
                it.copy(
                    scanning = true,
                    scanScanned = 0,
                    scanTotal = 0,
                    candidates = emptyList(),
                )
            }
            try {
                val scanForm = local.value.form
                container.lanScanner.scan(
                    localIp = localIp,
                    preferPs5ForTcp = scanForm.preferPs5ForTcp,
                    rpiPort = scanForm.httpPort.toIntOrNull() ?: 12800,
                    tcpPort = scanForm.tcpPort.toIntOrNull() ?: 9090,
                ).collect { progress: ScanProgress ->
                    local.update { current ->
                        current.copy(
                            scanScanned = progress.scanned,
                            scanTotal = progress.total,
                            candidates = if (progress.candidate != null) {
                                (current.candidates + progress.candidate)
                                    .distinctBy { it.host }
                                    .sortedWith(
                                        compareBy(
                                            {
                                                it.host.split('.').lastOrNull()?.toIntOrNull() ?: 0
                                            },
                                            { it.host },
                                        ),
                                    )
                            } else {
                                current.candidates
                            },
                        )
                    }
                }
                val count = local.value.candidates.size
                emit(
                    if (count == 0) {
                        "No se encontraron consolas en la red local"
                    } else {
                        "Escaneo completado: $count consola(s) encontrada(s)"
                    },
                )
            } catch (exception: Exception) {
                emit("Error durante el escaneo: ${exception.message}")
            } finally {
                local.update { it.copy(scanning = false) }
            }
        }
    }

    fun applyCandidate(candidate: LanCandidate) {
        local.update { current ->
            current.copy(
                form = current.form.copy(
                    host = candidate.host,
                    type = candidate.suggestedType,
                    httpPort = candidate.rpiPort?.toString() ?: current.form.httpPort,
                    tcpPort = candidate.controlPort?.toString() ?: current.form.tcpPort,
                    preferPs5ForTcp = candidate.suggestedType == ConsoleType.PS5_ETAHEN,
                ),
            )
        }
        val portText = candidate.openPorts.joinToString()
        emit("IP ${candidate.host} y puerto $portText aplicados. Guarda la configuración.")
    }

    fun startServer() {
        runCatching { RemoteSendService.startServer(container.appContext) }
            .onFailure { emit("No se pudo iniciar el servidor: ${it.message}") }
        emit("Iniciando servidor local…")
    }

    fun stopServer() {
        runCatching { RemoteSendService.stopServer(container.appContext) }
            .onFailure { emit("No se pudo detener el servidor: ${it.message}") }
    }

    private fun buildConsoleConfig(form: ConsoleForm): ConsoleConfig = ConsoleConfig(
        type = form.type,
        host = form.host.trim(),
        httpPort = form.httpPort.toInt(),
        tcpPort = form.tcpPort.toInt(),
        timeoutMs = form.timeoutMs.toLong(),
        pollIntervalMs = form.pollIntervalMs.toLong(),
        preferPs5ForTcp = form.preferPs5ForTcp,
    )

    private fun buildServerConfig(form: ConsoleForm): ServerConfig = ServerConfig(
        bindIp = form.bindIp.trim(),
        port = form.serverPort.toInt(),
        autoStart = form.autoStart,
        autoAdvance = form.autoAdvance,
        scanSubdirectories = form.scanSubdirectories,
        readMetadata = form.readMetadata,
        autoScanOnStartup = form.autoScanOnStartup,
        scanPath = form.scanPath.trim(),
    )

    private fun formFrom(console: ConsoleConfig, server: ServerConfig): ConsoleForm = ConsoleForm(
        type = console.type,
        host = console.host,
        httpPort = console.httpPort.toString(),
        tcpPort = console.tcpPort.toString(),
        timeoutMs = console.timeoutMs.toString(),
        pollIntervalMs = console.pollIntervalMs.toString(),
        preferPs5ForTcp = console.preferPs5ForTcp,
        bindIp = server.bindIp,
        serverPort = server.port.toString(),
        autoStart = server.autoStart,
        autoAdvance = server.autoAdvance,
        scanSubdirectories = server.scanSubdirectories,
        readMetadata = server.readMetadata,
        autoScanOnStartup = server.autoScanOnStartup,
        scanPath = server.scanPath,
    )

    private fun validate(form: ConsoleForm): String? {
        validateConnection(form)?.let { return it }
        val serverPort = form.serverPort.toIntOrNull()
        if (serverPort == null || serverPort !in 1..65535) {
            return "El puerto del servidor debe estar entre 1 y 65535"
        }
        if (form.bindIp.isNotBlank() && local.value.addresses.none { it.ip == form.bindIp }) {
            return "La IP del servidor no está disponible en este dispositivo"
        }
        return null
    }

    private fun validateConnection(form: ConsoleForm): String? {
        if (form.host.isNotBlank() && !NetworkUtils.isValidIpv4(form.host.trim())) {
            return "La IP de la consola no es válida"
        }
        val httpPort = form.httpPort.toIntOrNull()
        if (httpPort == null || httpPort !in 1..65535) {
            return "El puerto RPI debe estar entre 1 y 65535"
        }
        val tcpPort = form.tcpPort.toIntOrNull()
        if (tcpPort == null || tcpPort !in 1..65535) {
            return "El puerto TCP debe estar entre 1 y 65535"
        }
        val timeout = form.timeoutMs.toLongOrNull()
        if (timeout == null || timeout !in 1_000..60_000) {
            return "El timeout debe estar entre 1000 y 60000 ms"
        }
        val poll = form.pollIntervalMs.toLongOrNull()
        if (poll == null || poll !in 1_000..30_000) {
            return "El intervalo de progreso debe estar entre 1000 y 30000 ms"
        }
        return null
    }

    private fun emit(message: String) {
        _events.tryEmit(message)
    }
}
