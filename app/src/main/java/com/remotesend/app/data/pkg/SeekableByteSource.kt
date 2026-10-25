package com.remotesend.app.data.pkg

import android.content.ContentResolver
import android.net.Uri
import java.io.Closeable
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

interface SeekableByteSource : Closeable {
    fun size(): Long
    fun read(offset: Long, count: Int): ByteArray
}

class ByteArraySeekableSource(
    private val data: ByteArray,
) : SeekableByteSource {
    override fun size(): Long = data.size.toLong()

    override fun read(offset: Long, count: Int): ByteArray {
        if (offset < 0 || offset >= data.size || count <= 0) return ByteArray(0)
        val start = offset.toInt()
        val end = (start + count).coerceAtMost(data.size)
        return data.copyOfRange(start, end)
    }

    override fun close() = Unit
}

class FileSeekableSource(
    private val file: java.io.File,
) : SeekableByteSource {
    private val randomAccess = RandomAccessFile(file, "r")

    override fun size(): Long = randomAccess.length()

    override fun read(offset: Long, count: Int): ByteArray {
        if (count <= 0 || offset < 0 || offset >= randomAccess.length()) return ByteArray(0)
        val toRead = count.coerceAtMost((randomAccess.length() - offset).toInt())
        val buffer = ByteArray(toRead)
        randomAccess.seek(offset)
        var read = 0
        while (read < toRead) {
            val result = randomAccess.read(buffer, read, toRead - read)
            if (result < 0) break
            read += result
        }
        return if (read == toRead) buffer else buffer.copyOf(read)
    }

    override fun close() {
        runCatching { randomAccess.close() }
    }
}

class ContentUriSeekableSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : SeekableByteSource {

    private val descriptor = resolver.openFileDescriptor(uri, "r")
        ?: throw IOException("No se pudo abrir el documento")

    private val stream = FileInputStream(descriptor.fileDescriptor)
    private val channel = stream.channel
    private val length = descriptor.statSize.takeIf { it > 0 }
        ?: runCatching { channel.size() }.getOrDefault(0L)

    override fun size(): Long = length

    override fun read(offset: Long, count: Int): ByteArray {
        if (count <= 0 || offset < 0) return ByteArray(0)
        val direct = runCatching { readFromChannel(offset, count) }
        if (direct.isSuccess) return direct.getOrThrow()
        return readFromFreshStream(offset, count)
    }

    private fun readFromChannel(offset: Long, count: Int): ByteArray {
        val buffer = ByteArray(count)
        val byteBuffer = ByteBuffer.wrap(buffer)
        channel.position(offset)
        while (byteBuffer.hasRemaining()) {
            val read = channel.read(byteBuffer)
            if (read < 0) break
        }
        return if (byteBuffer.position() == count) {
            buffer
        } else {
            buffer.copyOf(byteBuffer.position())
        }
    }

    private fun readFromFreshStream(offset: Long, count: Int): ByteArray {
        resolver.openInputStream(uri)?.use { input ->
            input.skipFully(offset)
            val buffer = ByteArray(count)
            var read = 0
            while (read < count) {
                val result = input.read(buffer, read, count - read)
                if (result < 0) break
                read += result
            }
            return if (read == count) buffer else buffer.copyOf(read)
        }
        return ByteArray(0)
    }

    override fun close() {
        runCatching { stream.close() }
        runCatching { descriptor.close() }
    }
}

fun InputStream.skipFully(offset: Long) {
    var remaining = offset
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped <= 0) {
            if (read() < 0) return
            remaining--
        } else {
            remaining -= skipped
        }
    }
}
