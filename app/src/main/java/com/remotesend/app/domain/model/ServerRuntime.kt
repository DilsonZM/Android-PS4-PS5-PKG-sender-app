package com.remotesend.app.domain.model

sealed interface ServerRuntime {
    data object Stopped : ServerRuntime
    data object Starting : ServerRuntime

    data class Running(
        val host: String,
        val port: Int,
        val startedAt: Long,
    ) : ServerRuntime {
        val heartbeatUrl: String
            get() = "http://$host:$port/hb"

        val baseUrl: String
            get() = "http://$host:$port"
    }

    data class Error(
        val message: String,
    ) : ServerRuntime
}

sealed interface InstallRuntime {
    data object Idle : InstallRuntime

    data class Working(
        val queueItemId: String,
        val title: String,
        val status: QueueStatus,
        val progress: Int,
        val restSeconds: Long,
    ) : InstallRuntime

    data class Error(
        val message: String,
    ) : InstallRuntime
}

sealed interface StartResult {
    data object Started : StartResult

    data class Rejected(
        val message: String,
    ) : StartResult
}
