package com.remotesend.app.data.pkg

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PkgMetadataReaderTest {

    @Test
    fun `reads title and content id from a synthetic fPKG`() {
        val icon = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
        val sfo = sfo(
            mapOf(
                "TITLE" to "Mi Juego",
                "TITLE_ID" to "CUSA09311",
                "CONTENT_ID" to "UP0001-CUSA09311_00-ULCQUEST00000002",
                "APP_VER" to "01.02",
                "CATEGORY" to "gd",
            ),
        )
        val pkg = pkg(sfo, icon)

        val metadata = PkgMetadataReader().read(ByteArraySeekableSource(pkg))

        assertNotNull(metadata)
        assertEquals("Mi Juego", metadata?.title)
        assertEquals("CUSA09311", metadata?.titleId)
        assertEquals("UP0001-CUSA09311_00-ULCQUEST00000002", metadata?.contentId)
        assertEquals("01.02", metadata?.appVersion)
        assertEquals("gd", metadata?.category)
        assertArrayEquals(icon, metadata?.icon)
    }

    @Test
    fun `rejects files without the PKG magic`() {
        val invalid = ByteArray(128)
        assertNull(PkgMetadataReader().read(ByteArraySeekableSource(invalid)))
    }

    @Test
    fun `rejects truncated tables`() {
        val pkg = pkg(sfo(mapOf("TITLE" to "X")), byteArrayOf(1, 2, 3))
        val truncated = pkg.copyOfRange(0, 0x30)
        assertNull(PkgMetadataReader().read(ByteArraySeekableSource(truncated)))
    }

    private fun pkg(sfoBytes: ByteArray, icon: ByteArray): ByteArray {
        val headerSize = 0x20
        val tableOffset = headerSize
        val tableSize = 2 * 32
        val sfoOffset = tableOffset + tableSize
        val iconOffset = sfoOffset + sfoBytes.size
        val total = iconOffset + icon.size
        val buffer = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)

        buffer.put(0x7F.toByte())
        buffer.put('C'.code.toByte())
        buffer.put('N'.code.toByte())
        buffer.put('T'.code.toByte())
        buffer.position(0x10)
        buffer.putInt(2)
        buffer.position(0x18)
        buffer.putInt(tableOffset)

        buffer.position(tableOffset)
        buffer.putInt(0x1000)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(sfoOffset)
        buffer.putInt(sfoBytes.size)
        buffer.putLong(0L)

        buffer.putInt(0x1200)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(iconOffset)
        buffer.putInt(icon.size)
        buffer.putLong(0L)

        buffer.position(sfoOffset)
        buffer.put(sfoBytes)
        buffer.position(iconOffset)
        buffer.put(icon)

        return buffer.array()
    }

    private fun sfo(values: Map<String, String>): ByteArray {
        val keys = ByteArrayOutputStream()
        val data = ByteArrayOutputStream()
        val entries = ByteBuffer.allocate(values.size * 16).order(ByteOrder.LITTLE_ENDIAN)

        values.forEach { (key, value) ->
            val keyOffset = keys.size()
            keys.write(key.toByteArray(Charsets.UTF_8))
            keys.write(0)

            val dataOffset = data.size()
            val valueBytes = value.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
            data.write(valueBytes)

            entries.putShort(keyOffset.toShort())
            entries.putShort(0x0204.toShort())
            entries.putInt(valueBytes.size)
            entries.putInt(valueBytes.size)
            entries.putInt(dataOffset)
        }

        val keyTableOffset = 20 + values.size * 16
        val dataTableOffset = keyTableOffset + keys.size()
        val output = ByteBuffer.allocate(dataTableOffset + data.size()).order(ByteOrder.LITTLE_ENDIAN)
        output.putInt(0x46535000)
        output.putInt(0x00000101)
        output.putInt(keyTableOffset)
        output.putInt(dataTableOffset)
        output.putInt(values.size)
        output.put(entries.array())
        output.put(keys.toByteArray())
        output.put(data.toByteArray())
        return output.array()
    }
}
