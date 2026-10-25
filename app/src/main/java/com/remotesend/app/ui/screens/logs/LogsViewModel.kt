package com.remotesend.app.ui.screens.logs

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotesend.app.di.AppContainer
import com.remotesend.app.domain.model.LogEntry
import com.remotesend.app.domain.model.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogsUiState(
    val logs: List<LogEntry> = emptyList(),
    val enabledLevels: Set<LogLevel> = LogLevel.entries.toSet(),
    val query: String = "",
    val totalCount: Int = 0,
)

private data class LogsLocalState(
    val enabledLevels: Set<LogLevel> = LogLevel.entries.toSet(),
    val query: String = "",
)

class LogsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val local = MutableStateFlow(LogsLocalState())
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<LogsUiState> = combine(
        container.stateStore.state,
        local,
    ) { state, localState ->
        val query = localState.query.trim()
        val filtered = state.logs.filter { entry ->
            entry.level in localState.enabledLevels && (
                query.isBlank() ||
                    entry.message.contains(query, ignoreCase = true) ||
                    entry.source.contains(query, ignoreCase = true) ||
                    entry.details?.contains(query, ignoreCase = true) == true
                )
        }
        LogsUiState(
            logs = filtered,
            enabledLevels = localState.enabledLevels,
            query = localState.query,
            totalCount = state.logs.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LogsUiState(),
    )

    fun toggleLevel(level: LogLevel) {
        val current = local.value.enabledLevels
        val updated = if (level in current) current - level else current + level
        local.value = local.value.copy(enabledLevels = updated)
    }

    fun setQuery(value: String) {
        local.value = local.value.copy(query = value)
    }

    fun clearLogs() {
        viewModelScope.launch {
            container.stateStore.update { it.copy(logs = emptyList()) }
            emit("Registros eliminados")
        }
    }

    fun exportLogs(uri: Uri) {
        viewModelScope.launch {
            try {
                val logs = container.stateStore.snapshot().logs
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val text = buildString {
                    appendLine("RemoteSend - Registro de depuración")
                    appendLine("Exportado: ${format.format(Date())}")
                    appendLine("Entradas: ${logs.size}")
                    appendLine()
                    logs.asReversed().forEach { entry ->
                        appendLine(
                            "${format.format(Date(entry.timestamp))} " +
                                "[${entry.level}] ${entry.source}: ${entry.message}",
                        )
                        entry.details?.takeIf { it.isNotBlank() }?.let { details ->
                            appendLine("  $details")
                        }
                    }
                }
                withContext(Dispatchers.IO) {
                    container.appContext.contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    }
                }
                emit("Registros exportados")
            } catch (exception: Exception) {
                emit("No se pudieron exportar los registros: ${exception.message}")
            }
        }
    }

    private fun emit(message: String) {
        _events.tryEmit(message)
    }
}
