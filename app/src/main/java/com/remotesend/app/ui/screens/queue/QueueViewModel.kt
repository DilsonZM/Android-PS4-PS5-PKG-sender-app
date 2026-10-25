package com.remotesend.app.ui.screens.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotesend.app.di.AppContainer
import com.remotesend.app.domain.model.InstallRuntime
import com.remotesend.app.domain.model.QueueItem
import com.remotesend.app.domain.model.ServerRuntime
import com.remotesend.app.domain.model.StartResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QueueUiState(
    val queue: List<QueueItem> = emptyList(),
    val serverRuntime: ServerRuntime = ServerRuntime.Stopped,
    val runtime: InstallRuntime = InstallRuntime.Idle,
    val autoAdvance: Boolean = true,
    val busy: Boolean = false,
)

class QueueViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<QueueUiState> = combine(
        container.stateStore.state,
        container.serverManager.state,
        container.installationManager.runtime,
    ) { state, server, runtime ->
        QueueUiState(
            queue = state.queue,
            serverRuntime = server,
            runtime = runtime,
            autoAdvance = state.server.autoAdvance,
            busy = runtime is InstallRuntime.Working,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QueueUiState(),
    )

    fun startItem(itemId: String) {
        if (container.serverManager.state.value !is ServerRuntime.Running) {
            emit("Inicia el servidor local antes de instalar")
            return
        }
        viewModelScope.launch {
            when (val result = container.installationManager.start(itemId)) {
                is StartResult.Rejected -> emit(result.message)
                StartResult.Started -> Unit
            }
        }
    }

    fun startNext() {
        viewModelScope.launch {
            val next = container.queueRepository.nextQueued()
            if (next == null) {
                emit("No hay archivos en cola")
            } else {
                startItem(next.id)
            }
        }
    }

    fun resumeItem(itemId: String) {
        viewModelScope.launch {
            if (container.installationManager.isBusy) {
                val result = container.installationManager.resumeActive()
                if (result is StartResult.Rejected) emit(result.message)
            } else {
                startItem(itemId)
            }
        }
    }

    fun pauseActive() {
        viewModelScope.launch {
            container.installationManager.pauseActive()?.let(::emit)
        }
    }

    fun stopActive() {
        viewModelScope.launch {
            container.installationManager.stopActive()?.let(::emit)
        }
    }

    fun removeItem(itemId: String) {
        viewModelScope.launch {
            container.installationManager.removeItem(itemId)
            emit("Elemento eliminado de la cola")
        }
    }

    fun move(itemId: String, delta: Int) {
        viewModelScope.launch {
            container.queueRepository.move(itemId, delta)
        }
    }

    fun clearFinished() {
        viewModelScope.launch {
            container.queueRepository.clearFinished()
            emit("Elementos finalizados eliminados")
        }
    }

    fun resetQueue() {
        viewModelScope.launch {
            container.installationManager.resetQueue()
            emit("Cola reiniciada")
        }
    }

    fun toggleAutoAdvance() {
        viewModelScope.launch {
            container.settingsRepository.updateServer { it.copy(autoAdvance = !it.autoAdvance) }
        }
    }

    private fun emit(message: String) {
        _events.tryEmit(message)
    }
}
