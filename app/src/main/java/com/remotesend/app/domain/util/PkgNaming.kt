package com.remotesend.app.domain.util

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

object PkgNaming {
    private val titleIdRegex = Regex("""(CUSA|PPSA)\d{5}""", RegexOption.IGNORE_CASE)
    private val contentIdRegex =
        Regex("""[A-Z]{2}\d{4}-[A-Z]{4}\d{5}_00-[A-Z0-9]{16}""", RegexOption.IGNORE_CASE)
    private val invalidFileChars = Regex("""[^A-Za-z0-9._-]""")

    fun pkgId(uri: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    fun safeFileName(displayName: String): String {
        val trimmed = displayName.trim().ifBlank { "package.pkg" }
        var safe = invalidFileChars.replace(trimmed, "_")
        safe = safe.trimStart('.', '_')
        if (safe.isBlank()) safe = "package"
        if (!safe.lowercase(Locale.ROOT).endsWith(".pkg")) safe += ".pkg"
        if (safe.length > 120) {
            val extension = ".pkg"
            safe = safe.take(120 - extension.length).trimEnd('.', '_') + extension
        }
        return safe
    }

    fun titleIdFromName(displayName: String): String? =
        titleIdRegex.find(displayName)?.value?.uppercase(Locale.ROOT)

    fun contentIdFromName(displayName: String): String? =
        contentIdRegex.find(displayName)?.value?.uppercase(Locale.ROOT)

    fun titleIdFromContentId(contentId: String?): String? {
        if (contentId.isNullOrBlank()) return null
        val withoutPrefix = contentId.substringAfter('-', contentId)
        return titleIdRegex.find(withoutPrefix)?.value?.uppercase(Locale.ROOT)
            ?: titleIdRegex.find(contentId)?.value?.uppercase(Locale.ROOT)
    }

    fun isPkgName(displayName: String): Boolean =
        displayName.endsWith(".pkg", ignoreCase = true)

    fun inferCategory(displayName: String): String? {
        val lower = displayName.lowercase(Locale.ROOT)
        return when {
            Regex("""(^|[^a-z])(update|patch|upd)([^a-z]|$)""")
                .containsMatchIn(lower) -> "gp"

            Regex("""(^|[^a-z])(dlc|addon|add-on|additional|optional)([^a-z]|$)""")
                .containsMatchIn(lower) -> "ac"

            Regex("""(^|[^a-z])(fullgame|full-game|full_game|game)([^a-z]|$)""")
                .containsMatchIn(lower) -> "gd"

            else -> null
        }
    }

    fun effectiveCategory(category: String?, displayName: String): String? {
        return category?.takeIf { it.isNotBlank() } ?: inferCategory(displayName)
    }

    fun isBaseCategory(category: String?): Boolean =
        category?.lowercase(Locale.ROOT) in setOf(
            "gd", "gc", "gda", "gdc", "gdd", "gde", "gdk", "gdl", "gdo",
        )

    fun isUpdateCategory(category: String?): Boolean =
        category?.lowercase(Locale.ROOT) in setOf(
            "gp", "gpc", "gpd", "gpe", "gpk", "gpl",
        )

    fun isDlcCategory(category: String?): Boolean =
        category?.lowercase(Locale.ROOT) == "ac"

    fun packageSortRank(category: String?): Int = when {
        isBaseCategory(category) -> 0
        isUpdateCategory(category) -> 1
        isDlcCategory(category) -> 2
        else -> 3
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val index = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(index.toDouble())
        val decimals = if (index == 0) 0 else if (value >= 100) 0 else if (value >= 10) 1 else 2
        return "%.${decimals}f %s".format(Locale.US, value, units[index])
    }

    fun formatSpeed(bytesPerSecond: Long): String? {
        if (bytesPerSecond <= 0L) return null
        return formatBytes(bytesPerSecond) + "/s"
    }

    fun formatDuration(seconds: Long): String? {
        if (seconds <= 0L) return null
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${abs(secs)}s"
        }
    }
}
