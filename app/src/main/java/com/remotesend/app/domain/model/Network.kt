package com.remotesend.app.domain.model

data class NetworkAddress(
    val interfaceName: String,
    val ip: String,
    val prefixLength: Int = 24,
)

data class LanCandidate(
    val host: String,
    val openPorts: List<Int>,
    val suggestedType: ConsoleType,
    val rpiPort: Int? = null,
    val controlPort: Int? = null,
)

data class ScanProgress(
    val scanned: Int,
    val total: Int,
    val candidate: LanCandidate? = null,
)

@kotlinx.serialization.Serializable
data class BackupPayload(
    val schemaVersion: Int = 1,
    val exportedAt: Long = 0L,
    val appVersion: String = "",
    val packages: List<BackupPackage> = emptyList(),
    val queue: List<BackupQueueItem> = emptyList(),
)

@kotlinx.serialization.Serializable
data class BackupPackage(
    val displayName: String,
    val safeName: String,
    val title: String? = null,
    val titleId: String? = null,
    val contentId: String? = null,
    val sizeBytes: Long = 0L,
)

@kotlinx.serialization.Serializable
data class BackupQueueItem(
    val displayName: String,
    val safeName: String,
    val title: String? = null,
    val status: QueueStatus = QueueStatus.QUEUED,
)

data class BackupResult(
    val success: Boolean,
    val message: String,
    val matched: Int = 0,
    val skipped: Int = 0,
    val missing: List<String> = emptyList(),
)
