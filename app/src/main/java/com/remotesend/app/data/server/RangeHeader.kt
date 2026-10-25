package com.remotesend.app.data.server

sealed interface RangeResult {
    data object Ignore : RangeResult

    data class Valid(
        val start: Long,
        val length: Long,
    ) : RangeResult

    data object Unsatisfiable : RangeResult
}

object RangeHeader {
    fun parse(header: String?, size: Long): RangeResult {
        if (header.isNullOrBlank()) return RangeResult.Ignore
        if (!header.startsWith("bytes=", ignoreCase = true)) return RangeResult.Ignore
        if (size <= 0L) return RangeResult.Unsatisfiable

        val spec = header.substringAfter('=').substringBefore(',').trim()
        if (spec.isEmpty()) return RangeResult.Ignore

        val dash = spec.indexOf('-')
        if (dash < 0) return RangeResult.Ignore

        val startText = spec.substring(0, dash).trim()
        val endText = spec.substring(dash + 1).trim()

        if (startText.isEmpty()) {
            val suffix = endText.toLongOrNull() ?: return RangeResult.Ignore
            if (suffix <= 0L) return RangeResult.Unsatisfiable
            val length = suffix.coerceAtMost(size)
            return RangeResult.Valid(size - length, length)
        }

        val start = startText.toLongOrNull() ?: return RangeResult.Ignore
        if (start < 0L || start >= size) return RangeResult.Unsatisfiable

        val end = if (endText.isEmpty()) {
            size - 1
        } else {
            val parsedEnd = endText.toLongOrNull() ?: return RangeResult.Ignore
            parsedEnd.coerceAtMost(size - 1)
        }

        if (end < start) return RangeResult.Unsatisfiable
        return RangeResult.Valid(start, end - start + 1)
    }
}
