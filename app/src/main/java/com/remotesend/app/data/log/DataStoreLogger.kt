package com.remotesend.app.data.log

import android.util.Log
import com.remotesend.app.core.AppLogger
import com.remotesend.app.data.persist.StateStore
import com.remotesend.app.domain.model.LogEntry
import com.remotesend.app.domain.model.LogLevel
import com.remotesend.app.domain.model.PersistedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

class DataStoreLogger(
    private val stateStore: StateStore,
    private val scope: CoroutineScope,
    private val persistToStore: Boolean = true,
) : AppLogger {

    override fun debug(source: String, message: String, details: String?) =
        log(LogLevel.DEBUG, source, message, details)

    override fun info(source: String, message: String, details: String?) =
        log(LogLevel.INFO, source, message, details)

    override fun success(source: String, message: String, details: String?) =
        log(LogLevel.SUCCESS, source, message, details)

    override fun warn(source: String, message: String, details: String?) =
        log(LogLevel.WARN, source, message, details)

    override fun error(source: String, message: String, details: String?) =
        log(LogLevel.ERROR, source, message, details)

    override fun log(level: LogLevel, source: String, message: String, details: String?) {
        val priority = when (level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO, LogLevel.SUCCESS -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }
        Log.println(priority, "RemoteSend/$source", message + (details?.let { "\n$it" } ?: ""))

        if (!persistToStore) return

        val entry = LogEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            level = level,
            source = source,
            message = message,
            details = details,
        )
        scope.launch {
            runCatching {
                stateStore.update { state ->
                    state.copy(logs = (listOf(entry) + state.logs).take(PersistedState.MAX_LOGS))
                }
            }
        }
    }
}
