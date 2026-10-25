package com.remotesend.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PersistedState(
    val schemaVersion: Int = 1,
    val packages: List<PkgFile> = emptyList(),
    val queue: List<QueueItem> = emptyList(),
    val console: ConsoleConfig = ConsoleConfig(),
    val server: ServerConfig = ServerConfig(),
    val settings: AppSettings = AppSettings(),
    val logs: List<LogEntry> = emptyList(),
) {
    companion object {
        const val MAX_LOGS = 600
    }
}
