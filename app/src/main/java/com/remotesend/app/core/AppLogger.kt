package com.remotesend.app.core

import com.remotesend.app.domain.model.LogLevel

interface AppLogger {
    fun debug(source: String, message: String, details: String? = null)
    fun info(source: String, message: String, details: String? = null)
    fun success(source: String, message: String, details: String? = null)
    fun warn(source: String, message: String, details: String? = null)
    fun error(source: String, message: String, details: String? = null)

    fun log(level: LogLevel, source: String, message: String, details: String? = null)
}

object NoOpLogger : AppLogger {
    override fun debug(source: String, message: String, details: String?) = Unit
    override fun info(source: String, message: String, details: String?) = Unit
    override fun success(source: String, message: String, details: String?) = Unit
    override fun warn(source: String, message: String, details: String?) = Unit
    override fun error(source: String, message: String, details: String?) = Unit
    override fun log(level: LogLevel, source: String, message: String, details: String?) = Unit
}
