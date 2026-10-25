package com.remotesend.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotesend.app.data.service.RemoteSendService
import com.remotesend.app.di.AppContainer
import com.remotesend.app.domain.model.ConsoleConfig
import com.remotesend.app.domain.model.ConsoleConnectionState
import com.remotesend.app.domain.model.ConsoleConnectionStatus
import com.remotesend.app.domain.model.InstallRuntime
import com.remotesend.app.domain.model.QueueItem
import com.remotesend.app.domain.model.QueueStatus
import com.remotesend.app.domain.model.ServerConfig
import com.remotesend.app.domain.model.ServerRuntime
import com.remotesend.app.domain.model.StartResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val packagesCount: Int = 0,
    val queuedCount: Int = 0,
    val completedCount: Int = 0,
    val activeItem: QueueItem? = null,
    val serverState: ServerRuntime = ServerRuntime.Stopped,
    val serverConfig: ServerConfig = ServerConfig(),
    val consoleConfig: ConsoleConfig = ConsoleConfig(),
    val runtime: InstallRuntime = InstallRuntime.Idle,
    val connection: ConsoleConnectionState = ConsoleConnectionState(),
) {
    val checking: Boolean
        get() = connection.status == ConsoleConnectionStatus.CHECKING
}

class HomeViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        container.stateStore.state,
        container.serverManager.state,
        container.installationManager.runtime,
        container.consoleDetector.connection,
    ) { state, server, runtime, connection ->
        HomeUiState(
            packagesCount = state.packages.size,
            queuedCount = state.queue.count { it.status == QueueStatus.QUEUED },
            completedCount = state.queue.count {
                it.status == QueueStatus.COMPLETED ||
                    it.status == QueueStatus.SENT ||
                    it.status == QueueStatus.EXISTS
            },
            activeItem = state.queue.firstOrNull {
                it.status == QueueStatus.INSTALLING || it.status == QueueStatus.PAUSED
            },
            serverState = server,
            serverConfig = state.server,
            consoleConfig = state.console,
            runtime = runtime,
            connection = connection,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        viewModelScope.launch {
            val config = container.stateStore.snapshot().console
            if (config.host.isNotBlank() &&
                container.consoleDetector.connection.value.status == ConsoleConnectionStatus.UNKNOWN
            ) {
                container.consoleDetector.detect(config)
            }
        }
    }

    fun startServer() {
        runCatching {
            RemoteSendService.startServer(container.appContext)
        }.onFailure {
            emit("No se pudo iniciar el servicio: ${it.message}")
        }
        emit("Iniciando servidor local…")
    }

    fun stopServer() {
        runCatching {
            RemoteSendService.stopServer(container.appContext)
        }.onFailure {
            emit("No se pudo detener el servidor: ${it.message}")
        }
    }

    fun checkConsole() {
        if (container.consoleDetector.connection.value.status == ConsoleConnectionStatus.CHECKING) {
            return
        }
        viewModelScope.launch {
            val config = container.stateStore.snapshot().console
            if (config.host.isBlank()) {
                emit("Configura la IP de la consola en la pestaña Consola")
                return@launch
            }
            val result = container.consoleDetector.detect(config)
            emit(result.message)
        }
    }

    fun toggleAutoAdvance() {
        viewModelScope.launch {
            container.settingsRepository.updateServer { it.copy(autoAdvance = !it.autoAdvance) }
        }
    }

    fun pauseActive() {
        viewModelScope.launch {
            container.installationManager.pauseActive()?.let(::emit)
        }
    }

    fun resumeActive() {
        viewModelScope.launch {
            val result = container.installationManager.resumeActive()
            if (result is StartResult.Rejected) {
                emit(result.message)
            }
        }
    }

    fun stopActive() {
        viewModelScope.launch {
            container.installationManager.stopActive()?.let(::emit)
        }
    }

    private fun emit(message: String) {
        _events.tryEmit(message)
    }
}
