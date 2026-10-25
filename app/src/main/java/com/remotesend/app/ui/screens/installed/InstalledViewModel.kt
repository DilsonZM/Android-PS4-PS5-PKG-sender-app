package com.remotesend.app.ui.screens.installed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotesend.app.di.AppContainer
import com.remotesend.app.domain.model.InstalledTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InstalledUiState(
    val titles: List<InstalledTitle> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val warning: String? = null,
    val actionInProgress: Boolean = false,
    val lastCheckedAt: Long? = null,
)

class InstalledViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InstalledUiState())
    val uiState: StateFlow<InstalledUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = container.installedRepository.scan()
            _uiState.update {
                it.copy(
                    loading = false,
                    titles = result.titles,
                    error = result.unsupportedReason,
                    warning = result.warning,
                    lastCheckedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    fun uninstallGame(titleId: String) {
        performUninstall("juego") {
            container.installedRepository.uninstallGame(titleId)
        }
    }

    fun uninstallUpdate(titleId: String) {
        performUninstall("actualización") {
            container.installedRepository.uninstallUpdate(titleId)
        }
    }

    fun uninstallDlc(contentId: String) {
        performUninstall("DLC") {
            container.installedRepository.uninstallDlc(contentId)
        }
    }

    private fun performUninstall(
        label: String,
        action: suspend () -> Result<Unit>,
    ) {
        if (_uiState.value.actionInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true) }
            val result = action()
            _uiState.update { it.copy(actionInProgress = false) }
            result.onSuccess {
                emit("Desinstalación de $label enviada")
                delay(1_500L)
                refresh()
            }.onFailure { exception ->
                emit(exception.message ?: "No se pudo desinstalar el $label")
            }
        }
    }

    private fun emit(message: String) {
        _events.tryEmit(message)
    }
}
