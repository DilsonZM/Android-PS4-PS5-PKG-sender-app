package com.remotesend.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class QueueStatus {
    QUEUED,
    INSTALLING,
    PAUSED,
    STOPPED,
    COMPLETED,
    SENT,
    FAILED,
    EXISTS,
}

val QueueStatus.isTerminal: Boolean
    get() = this == QueueStatus.COMPLETED ||
        this == QueueStatus.SENT ||
        this == QueueStatus.FAILED ||
        this == QueueStatus.EXISTS

val QueueStatus.isRunning: Boolean
    get() = this == QueueStatus.INSTALLING

@Serializable
data class QueueItem(
    val id: String,
    val pkgId: String,
    val displayName: String,
    val title: String? = null,
    val titleId: String? = null,
    val category: String? = null,
    val appVersion: String? = null,
    val iconPath: String? = null,
    val sizeBytes: Long = 0L,
    val status: QueueStatus = QueueStatus.QUEUED,
    val progress: Int = 0,
    val restSeconds: Long = 0L,
    val transferredBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val taskId: Long? = null,
    val message: String? = null,
    val addedAt: Long = 0L,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val consoleType: ConsoleType? = null,
    val serverUrl: String? = null,
) {
    val effectiveTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: displayName
}

data class AddToQueueResult(
    val added: Boolean,
    val reason: String? = null,
)
