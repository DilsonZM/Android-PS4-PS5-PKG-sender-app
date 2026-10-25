package com.remotesend.app.data.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

internal val networkJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

internal fun JsonObject.stringValue(key: String): String? {
    val element = this[key] as? JsonPrimitive ?: return null
    return element.contentOrNull
}

internal fun JsonObject.longValue(key: String): Long? {
    val element = this[key] as? JsonPrimitive ?: return null
    val text = element.contentOrNull?.trim() ?: return null
    return when {
        text.startsWith("0x", ignoreCase = true) ->
            text.substring(2).toLongOrNull(16)

        else -> text.toLongOrNull() ?: text.toDoubleOrNull()?.toLong()
    }
}

internal fun JsonObject.booleanValue(key: String): Boolean? {
    val element = this[key] as? JsonPrimitive ?: return null
    return element.booleanOrNull ?: element.contentOrNull?.toBooleanStrictOrNull()
}

internal fun parseJsonObject(raw: String): JsonObject? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("{")) return null
    return runCatching {
        networkJson.parseToJsonElement(trimmed) as? JsonObject
    }.getOrNull()
}

internal fun consoleErrorMessage(code: Long?): String {
    return when (code) {
        2157510681L -> "La tarea no existe en la consola (puede haber finalizado)"
        2157510663L -> "El paquete parece estar instalado (código 2157510663)"
        2157510677L -> "El paquete ya está instalado en la consola"
        2157510789L -> "No hay espacio suficiente en la consola"
        -2135809020L -> "La consola no pudo descargar el PKG (HTTP 404). Revisa el servidor local."
        null -> "Error desconocido de la consola"
        else -> "Error de la consola ($code)"
    }
}
