package com.remotesend.app.data.pkg

import com.remotesend.app.core.AppLogger
import com.remotesend.app.core.NoOpLogger
import com.remotesend.app.domain.model.PkgMetadata

/**
 * Minimal PKG reader for fPKG files. It reads the file table, extracts
 * PARAM.SFO and the icon0 entry without decrypting the package payload.
 */
class PkgMetadataReader(
    private val logger: AppLogger = NoOpLogger,
) {
    fun read(source: SeekableByteSource): PkgMetadata? {
        return try {
            parse(source)
        } catch (exception: Exception) {
            logger.debug(
                "PkgReader",
                "No se pudo leer la cabecera PKG",
                exception.message,
            )
            null
        }
    }

    private fun parse(source: SeekableByteSource): PkgMetadata? {
        if (source.size() < HEADER_SIZE) return null

        val header = source.read(0, HEADER_SIZE)
        if (header.size < HEADER_SIZE) return null
        if (!isPkgMagic(header)) return null

        val tableEntries = readU32BE(header, 0x10)
        val tableOffset = readU32BE(header, 0x18).toLong() and 0xFFFF_FFFFL

        if (tableEntries !in 1..MAX_TABLE_ENTRIES) return null
        if (tableOffset <= 0L || tableOffset >= source.size()) return null

        val tableSize = tableEntries * ENTRY_SIZE
        if (tableSize > MAX_TABLE_BYTES) return null
        if (tableOffset + tableSize > source.size()) return null

        val table = source.read(tableOffset, tableSize)
        if (table.size < tableSize) return null

        var sfoOffset = -1L
        var sfoSize = -1
        var iconOffset = -1L
        var iconSize = -1

        for (index in 0 until tableEntries) {
            val base = index * ENTRY_SIZE
            if (base + ENTRY_SIZE > table.size) break
            val id = readU32BE(table, base)
            val entryOffset = readU32BE(table, base + 16).toLong() and 0xFFFF_FFFFL
            val entrySize = readU32BE(table, base + 20)
            when (id) {
                SFO_ENTRY_ID -> {
                    sfoOffset = entryOffset
                    sfoSize = entrySize
                }

                ICON_ENTRY_ID -> {
                    iconOffset = entryOffset
                    iconSize = entrySize
                }
            }
        }

        var title: String? = null
        var titleId: String? = null
        var contentId: String? = null
        var appVersion: String? = null
        var category: String? = null

        if (sfoOffset >= 0 && sfoSize > 0 && sfoSize <= MAX_SFO_BYTES) {
            if (sfoOffset + sfoSize <= source.size()) {
                val sfoBytes = source.read(sfoOffset, sfoSize)
                if (sfoBytes.size == sfoSize) {
                    val values = parseSfo(sfoBytes)
                    title = values["TITLE"] as? String
                    titleId = values["TITLE_ID"] as? String
                    contentId = values["CONTENT_ID"] as? String
                    appVersion = values["APP_VER"] as? String
                    category = values["CATEGORY"] as? String
                }
            }
        }

        var icon: ByteArray? = null
        if (iconOffset >= 0 && iconSize in 1..MAX_ICON_BYTES) {
            if (iconOffset + iconSize <= source.size()) {
                val bytes = source.read(iconOffset, iconSize)
                if (bytes.size == iconSize) icon = bytes
            }
        }

        if (title == null && titleId == null && contentId == null && icon == null) {
            return null
        }

        return PkgMetadata(
            title = title?.trim()?.takeIf { it.isNotBlank() },
            titleId = titleId?.trim()?.takeIf { it.isNotBlank() },
            contentId = contentId?.trim()?.takeIf { it.isNotBlank() },
            appVersion = appVersion?.trim()?.takeIf { it.isNotBlank() },
            category = category?.trim()?.takeIf { it.isNotBlank() },
            icon = icon,
        )
    }

    private fun parseSfo(data: ByteArray): Map<String, Any> {
        if (data.size < SFO_HEADER_SIZE) return emptyMap()
        if (readU32LE(data, 0) != SFO_MAGIC) return emptyMap()

        val keyTableOffset = readU32LE(data, 8)
        val dataTableOffset = readU32LE(data, 12)
        val entries = readU32LE(data, 16)

        if (entries !in 1..MAX_SFO_ENTRIES) return emptyMap()
        if (keyTableOffset < SFO_HEADER_SIZE || keyTableOffset >= data.size) return emptyMap()
        if (dataTableOffset < SFO_HEADER_SIZE || dataTableOffset > data.size) return emptyMap()

        val values = mutableMapOf<String, Any>()
        for (index in 0 until entries) {
            val base = SFO_HEADER_SIZE + index * SFO_ENTRY_SIZE
            if (base + SFO_ENTRY_SIZE > data.size) break

            val keyOffset = readU16LE(data, base)
            val format = readU16LE(data, base + 2)
            val length = readU32LE(data, base + 4)
            val dataOffset = readU32LE(data, base + 12)

            val key = readNullTerminated(data, keyTableOffset + keyOffset) ?: continue
            val absolute = dataTableOffset + dataOffset
            if (length <= 0 || absolute < 0 || absolute >= data.size) continue

            when (format) {
                SFO_FORMAT_UTF8 -> {
                    val available = (data.size - absolute).coerceAtMost(length)
                    val textLength = (available - 1).coerceAtLeast(0)
                    values[key] = String(data, absolute, textLength, Charsets.UTF_8)
                }

                SFO_FORMAT_INTEGER -> {
                    if (absolute + 4 <= data.size) {
                        values[key] = readU32LE(data, absolute)
                    }
                }
            }
        }
        return values
    }

    private fun readNullTerminated(data: ByteArray, start: Int): String? {
        if (start !in data.indices) return null
        var end = start
        while (end < data.size && data[end] != 0.toByte()) end++
        if (end == start) return null
        return String(data, start, end - start, Charsets.UTF_8)
    }

    private fun isPkgMagic(header: ByteArray): Boolean {
        return header.size >= 4 &&
            header[0] == 0x7F.toByte() &&
            header[1] == 'C'.code.toByte() &&
            header[2] == 'N'.code.toByte() &&
            header[3] == 'T'.code.toByte()
    }

    companion object {
        private const val HEADER_SIZE = 0x20
        private const val ENTRY_SIZE = 32
        private const val MAX_TABLE_ENTRIES = 10_000
        private const val MAX_TABLE_BYTES = 320_000
        private const val SFO_ENTRY_ID = 0x1000
        private const val ICON_ENTRY_ID = 0x1200
        private const val MAX_SFO_BYTES = 1_000_000
        private const val MAX_ICON_BYTES = 4_000_000
        private const val SFO_MAGIC = 0x46535000
        private const val SFO_HEADER_SIZE = 20
        private const val SFO_ENTRY_SIZE = 16
        private const val MAX_SFO_ENTRIES = 2_000
        private const val SFO_FORMAT_UTF8 = 0x0204
        private const val SFO_FORMAT_INTEGER = 0x0404

        fun readU32BE(data: ByteArray, offset: Int): Int {
            if (offset + 4 > data.size) return 0
            return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
        }

        fun readU32LE(data: ByteArray, offset: Int): Int {
            if (offset + 4 > data.size) return 0
            return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
        }

        fun readU16LE(data: ByteArray, offset: Int): Int {
            if (offset + 2 > data.size) return 0
            return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)
        }
    }
}
