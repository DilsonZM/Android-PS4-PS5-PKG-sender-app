package com.remotesend.app.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotesend.app.BuildConfig
import com.remotesend.app.di.AppContainer
import com.remotesend.app.domain.model.AppSettings
import com.remotesend.app.domain.model.FrameRateMode
import com.remotesend.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val logsCount: Int = 0,
    val backupBusy: Boolean = false,
    val backupMessage: String? = null,
    val appVersion: String = BuildConfig.VERSION_NAME,
)

private data class SettingsLocalState(
    val backupBusy: Boolean = false,
    val backupMessage: String? = null,
)

class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val local = MutableStateFlow(SettingsLocalState())
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        container.stateStore.state,
        local,
    ) { state, localState ->
        SettingsUiState(
            settings = state.settings,
            logsCount = state.logs.size,
            backupBusy = localState.backupBusy,
            backupMessage = localState.backupMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            container.settingsRepository.updateSettings { it.copy(themeMode = mode) }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.updateSettings { it.copy(dynamicColor = enabled) }
        }
    }

    fun setFrameRate(mode: FrameRateMode) {
        viewModelScope.launch {
            container.settingsRepository.updateSettings { it.copy(frameRateMode = mode) }
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.updateSettings { it.copy(notificationsEnabled = enabled) }
        }
    }

    fun setDeveloperMode(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.updateSettings { it.copy(developerMode = enabled) }
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            local.value = SettingsLocalState(backupBusy = true, backupMessage = null)
            val result = container.backupManager.exportTo(uri)
            local.value = SettingsLocalState(
                backupBusy = false,
                backupMessage = result.message,
            )
            emit(result.message)
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            local.value = SettingsLocalState(backupBusy = true, backupMessage = null)
            val result = container.backupManager.importFrom(uri)
            local.value = SettingsLocalState(
                backupBusy = false,
                backupMessage = result.message,
            )
            emit(result.message)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            container.stateStore.update { it.copy(logs = emptyList()) }
            emit("Registros eliminados")
        }
    }

    private fun emit(message: String) {
        _events.tryEmit(message)
    }
}
