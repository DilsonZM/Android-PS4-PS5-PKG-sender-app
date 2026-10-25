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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val logsCount: Int = 0,
    val backupBusy: Boolean = false,
    val backupMessage: String? = null,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val consoleHost: String = "",
    val ftpBusy: Boolean = false,
    val ftpMessage: String? = null,
    val ftpRemotePath: String? = null,
)

private data class SettingsLocalState(
    val backupBusy: Boolean = false,
    val backupMessage: String? = null,
    val ftpBusy: Boolean = false,
    val ftpMessage: String? = null,
    val ftpRemotePath: String? = null,
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
            consoleHost = state.console.host,
            ftpBusy = localState.ftpBusy,
            ftpMessage = localState.ftpMessage,
            ftpRemotePath = localState.ftpRemotePath,
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
            local.value = local.value.copy(backupBusy = true, backupMessage = null)
            val result = container.backupManager.exportTo(uri)
            local.value = local.value.copy(
                backupBusy = false,
                backupMessage = result.message,
            )
            emit(result.message)
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            local.value = local.value.copy(backupBusy = true, backupMessage = null)
            val result = container.backupManager.importFrom(uri)
            local.value = local.value.copy(
                backupBusy = false,
                backupMessage = result.message,
            )
            emit(result.message)
        }
    }

    fun uploadToPs4(uri: Uri) {
        if (local.value.ftpBusy) return
        viewModelScope.launch {
            val host = container.stateStore.snapshot().console.host
            if (host.isBlank()) {
                local.update {
                    it.copy(
                        ftpMessage = "Configura la IP de la consola en Consola",
                        ftpRemotePath = null,
                    )
                }
                return@launch
            }
            local.update {
                it.copy(ftpBusy = true, ftpMessage = null, ftpRemotePath = null)
            }
            val result = container.ftpUploader.upload(
                host = host,
                port = 2121,
                remoteDir = "/user/data/RemoteSend",
                uri = uri,
            )
            result.onSuccess { upload ->
                local.update {
                    it.copy(
                        ftpBusy = false,
                        ftpMessage = "Archivo enviado (${upload.bytes} bytes)",
                        ftpRemotePath = upload.remotePath,
                    )
                }
                emit("Enviado a ${upload.remotePath}")
            }.onFailure { exception ->
                local.update {
                    it.copy(
                        ftpBusy = false,
                        ftpMessage = exception.message ?: "No se pudo enviar el archivo",
                        ftpRemotePath = null,
                    )
                }
                emit(exception.message ?: "No se pudo enviar el archivo")
            }
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
