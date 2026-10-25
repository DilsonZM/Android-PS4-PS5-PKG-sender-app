package com.remotesend.app.ui

import com.remotesend.app.domain.model.ConsoleType
import com.remotesend.app.domain.model.FrameRateMode
import com.remotesend.app.domain.model.LogLevel
import com.remotesend.app.domain.model.QueueStatus
import com.remotesend.app.domain.model.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun statusLabel(status: QueueStatus): String = when (status) {
    QueueStatus.QUEUED -> "En cola"
    QueueStatus.INSTALLING -> "Instalando"
    QueueStatus.PAUSED -> "Pausado"
    QueueStatus.STOPPED -> "Detenido"
    QueueStatus.COMPLETED -> "Completado"
    QueueStatus.SENT -> "Enviado"
    QueueStatus.FAILED -> "Error"
    QueueStatus.EXISTS -> "Ya existe"
}

fun consoleTypeLabel(type: ConsoleType): String = when (type) {
    ConsoleType.AUTO -> "Automático"
    ConsoleType.PS4_RPI -> "PS4 · flatZ RPI"
    ConsoleType.PS4_GOLDHEN -> "PS4 · GoldHEN"
    ConsoleType.PS5_ETAHEN -> "PS5 · etaHEN"
}

fun frameRateLabel(mode: FrameRateMode): String = when (mode) {
    FrameRateMode.AUTO -> "Automático"
    FrameRateMode.FPS_60 -> "60 FPS"
    FrameRateMode.FPS_120 -> "120 FPS"
}

fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Sistema"
    ThemeMode.LIGHT -> "Claro"
    ThemeMode.DARK -> "Oscuro"
}

fun logLevelLabel(level: LogLevel): String = when (level) {
    LogLevel.DEBUG -> "DEBUG"
    LogLevel.INFO -> "INFO"
    LogLevel.SUCCESS -> "OK"
    LogLevel.WARN -> "AVISO"
    LogLevel.ERROR -> "ERROR"
}

fun categoryLabel(category: String?): String? = when (category?.lowercase(Locale.ROOT)) {
    "gd", "gc" -> "Juego base"
    "gp" -> "Actualización"
    "ac" -> "DLC"
    "bd" -> "Blu-ray"
    "gda", "gdc", "gdd", "gde", "gdk", "gdl", "gdo" -> "Aplicación"
    "gpc", "gpd", "gpe", "gpk", "gpl" -> "Actualización"
    null, "" -> null
    else -> category.uppercase(Locale.ROOT)
}

fun formatTimestamp(millis: Long): String {
    val now = System.currentTimeMillis()
    val sameDay = now - millis < 24 * 60 * 60 * 1000L
    val pattern = if (sameDay) "HH:mm:ss" else "dd/MM HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}

fun formatDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))
