package com.remotesend.app.data.server

import com.remotesend.app.core.NoOpLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.Socket

class PkgHttpServerTest {
    private val payload = "0123456789".toByteArray()
    private lateinit var scope: CoroutineScope
    private lateinit var server: PkgHttpServer
    private var port = 0

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        server = PkgHttpServer(
            port = 0,
            provider = FakeProvider(payload),
            logger = NoOpLogger,
            scope = scope,
        )
        port = server.start()
    }

    @After
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    @Test
    fun `serves heartbeat JSON`() = runBlocking {
        val response = request("GET /hb HTTP/1.1\r\nHost: localhost\r\n\r\n")
        assertEquals(200, response.code)
        assertTrue(response.bodyText.contains("\"status\":\"ok\""))
        assertTrue(response.bodyText.contains("\"packages\":1"))
    }

    @Test
    fun `serves full package`() = runBlocking {
        val response = request("GET /pkg/abc/file.pkg HTTP/1.1\r\nHost: localhost\r\n\r\n")
        assertEquals(200, response.code)
        assertEquals("10", response.headers["content-length"])
        assertEquals("bytes", response.headers["accept-ranges"])
        assertArrayEquals(payload, response.body)
    }

    @Test
    fun `serves a byte range`() = runBlocking {
        val response = request(
            "GET /pkg/abc/file.pkg HTTP/1.1\r\nHost: localhost\r\nRange: bytes=2-5\r\n\r\n",
        )
        assertEquals(206, response.code)
        assertEquals("bytes 2-5/10", response.headers["content-range"])
        assertArrayEquals(payload.copyOfRange(2, 6), response.body)
    }

    @Test
    fun `supports HEAD without body`() = runBlocking {
        val response = request("HEAD /pkg/abc/file.pkg HTTP/1.1\r\nHost: localhost\r\n\r\n")
        assertEquals(200, response.code)
        assertEquals("10", response.headers["content-length"])
        assertEquals(0, response.body.size)
    }

    @Test
    fun `returns 416 for unsatisfiable range`() = runBlocking {
        val response = request(
            "GET /pkg/abc/file.pkg HTTP/1.1\r\nHost: localhost\r\nRange: bytes=50-\r\n\r\n",
        )
        assertEquals(416, response.code)
        assertEquals("bytes */10", response.headers["content-range"])
    }

    @Test
    fun `returns 404 for unknown package`() = runBlocking {
        val response = request("GET /pkg/nope/file.pkg HTTP/1.1\r\nHost: localhost\r\n\r\n")
        assertEquals(404, response.code)
    }

    private fun request(raw: String): HttpResponse {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().write(raw.toByteArray(Charsets.US_ASCII))
            socket.getOutputStream().flush()
            val bytes = socket.getInputStream().readBytes()
            val separator = indexOf(bytes, byteArrayOf(13, 10, 13, 10))
            val headerText = String(bytes, 0, separator, Charsets.US_ASCII)
            val body = bytes.copyOfRange(separator + 4, bytes.size)
            val lines = headerText.split("\r\n")
            val code = lines.first().split(' ')[1].toInt()
            val headers = lines.drop(1)
                .filter { it.contains(':') }
                .associate {
                    val index = it.indexOf(':')
                    it.substring(0, index).trim().lowercase() to it.substring(index + 1).trim()
                }
            return HttpResponse(code, headers, body)
        }
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray): Int {
        if (pattern.isEmpty()) return 0
        for (index in 0..data.size - pattern.size) {
            var matches = true
            for (offset in pattern.indices) {
                if (data[index + offset] != pattern[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return index
        }
        return data.size
    }

    private data class HttpResponse(
        val code: Int,
        val headers: Map<String, String>,
        val body: ByteArray,
    ) {
        val bodyText: String get() = String(body, Charsets.UTF_8)
    }

    private class FakeProvider(
        private val bytes: ByteArray,
    ) : HttpContentProvider {
        private val pkg = ServerPackage(
            id = "abc",
            safeName = "file.pkg",
            displayName = "file.pkg",
            title = "Test",
            titleId = "CUSA00001",
            sizeBytes = bytes.size.toLong(),
            hasIcon = false,
        )

        override suspend fun packages(): List<ServerPackage> = listOf(pkg)

        override suspend fun openStream(packageId: String, offset: Long): InputStream? {
            if (packageId != "abc") return null
            return ByteArrayInputStream(bytes).apply {
                var remaining = offset
                while (remaining > 0) {
                    val skipped = skip(remaining)
                    if (skipped <= 0) break
                    remaining -= skipped
                }
            }
        }

        override suspend fun icon(packageId: String): ByteArray? = null
    }
}
