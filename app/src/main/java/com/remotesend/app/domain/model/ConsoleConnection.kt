package com.remotesend.app.domain.model

enum class ConsoleConnectionStatus {
    UNKNOWN,
    CHECKING,
    CONNECTED,
    DISCONNECTED,
}

data class ConsoleConnectionState(
    val status: ConsoleConnectionStatus = ConsoleConnectionStatus.UNKNOWN,
    val type: ConsoleType = ConsoleType.AUTO,
    val message: String? = null,
    val latencyMs: Long? = null,
    val ambiguousTcp: Boolean = false,
    val checkedAt: Long? = null,
)
