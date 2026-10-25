package com.remotesend.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConsoleType {
    AUTO,
    PS4_RPI,
    PS4_GOLDHEN,
    PS5_ETAHEN,
}

val ConsoleType.isTcpBased: Boolean
    get() = this == ConsoleType.PS4_GOLDHEN || this == ConsoleType.PS5_ETAHEN

@Serializable
data class ConsoleConfig(
    val type: ConsoleType = ConsoleType.AUTO,
    val host: String = "",
    val httpPort: Int = 12800,
    val tcpPort: Int = 9090,
    val timeoutMs: Long = 3_500L,
    val pollIntervalMs: Long = 2_200L,
    val preferPs5ForTcp: Boolean = true,
) {
    val address: String
        get() = if (host.isBlank()) "" else "$host:$httpPort"
}

data class DetectionResult(
    val reachable: Boolean,
    val type: ConsoleType = ConsoleType.AUTO,
    val message: String,
    val latencyMs: Long? = null,
    val ambiguousTcp: Boolean = false,
    val httpPort: Int? = null,
    val tcpPort: Int? = null,
)

data class ConsoleHealth(
    val reachable: Boolean,
    val message: String,
    val latencyMs: Long? = null,
)

sealed interface ConsoleInstallResult {
    data class Accepted(
        val taskId: Long? = null,
        val message: String? = null,
    ) : ConsoleInstallResult

    data class AlreadyInstalled(
        val message: String,
    ) : ConsoleInstallResult

    data class Failure(
        val message: String,
        val code: Long? = null,
    ) : ConsoleInstallResult
}

sealed interface TaskProgress {
    data class Running(
        val percent: Int,
        val restSeconds: Long,
        val transferredBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val detail: String? = null,
    ) : TaskProgress

    data object Completed : TaskProgress

    data class TaskMissing(
        val message: String,
    ) : TaskProgress

    data class Failure(
        val message: String,
        val code: Long? = null,
    ) : TaskProgress

    data object Unsupported : TaskProgress
}
