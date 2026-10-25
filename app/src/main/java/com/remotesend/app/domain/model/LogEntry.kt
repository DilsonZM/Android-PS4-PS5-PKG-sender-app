package com.remotesend.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LogLevel {
    DEBUG,
    INFO,
    SUCCESS,
    WARN,
    ERROR,
}

@Serializable
data class LogEntry(
    val id: String,
    val timestamp: Long,
    val level: LogLevel,
    val source: String,
    val message: String,
    val details: String? = null,
)
