package com.remotesend.app.data.network

import com.remotesend.app.core.NoOpLogger
import com.remotesend.app.domain.model.ConsoleConfig
import com.remotesend.app.domain.model.ConsoleConnectionStatus
import com.remotesend.app.domain.model.ConsoleType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class ConsoleDetectorTest {

    @Test
    fun `auto detection finds flatZ RPI`() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"success","exists":false}"""),
        )
        val closedTcpPort = ServerSocket(0).use { it.localPort }
        val detector = ConsoleDetector(ConsoleClientFactory(NoOpLogger), NoOpLogger)

        val result = detector.detect(
            ConsoleConfig(
                type = ConsoleType.AUTO,
                host = "127.0.0.1",
                httpPort = server.port,
                tcpPort = closedTcpPort,
                timeoutMs = 800,
            ),
        )

        assertTrue(result.reachable)
        assertEquals(ConsoleType.PS4_RPI, result.type)
        assertEquals(server.port, result.httpPort)
        assertEquals(ConsoleConnectionStatus.CONNECTED, detector.connection.value.status)
        assertEquals(ConsoleType.PS4_RPI, detector.connection.value.type)
        server.shutdown()
    }

    @Test
    fun `auto detection falls back to PS4RPI port 12801`() = runBlocking {
        val server = MockWebServer()
        try {
            server.start(12801)
        } catch (exception: Exception) {
            org.junit.Assume.assumeNoException("Puerto 12801 ocupado en el host", exception)
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"success","exists":false}"""),
        )
        val closedPort = ServerSocket(0).use { it.localPort }
        val detector = ConsoleDetector(ConsoleClientFactory(NoOpLogger), NoOpLogger)

        val result = detector.detect(
            ConsoleConfig(
                type = ConsoleType.AUTO,
                host = "127.0.0.1",
                httpPort = closedPort,
                tcpPort = closedPort,
                timeoutMs = 700,
            ),
        )

        assertTrue(result.reachable)
        assertEquals(ConsoleType.PS4_RPI, result.type)
        assertEquals(12801, result.httpPort)
        server.shutdown()
    }

    @Test
    fun `explicit probe fails when port is closed`() = runBlocking {
        val closedPort = ServerSocket(0).use { it.localPort }
        val detector = ConsoleDetector(ConsoleClientFactory(NoOpLogger), NoOpLogger)

        val result = detector.detect(
            ConsoleConfig(
                type = ConsoleType.PS4_RPI,
                host = "127.0.0.1",
                httpPort = closedPort,
                timeoutMs = 400,
            ),
        )

        assertFalse(result.reachable)
        assertEquals(ConsoleConnectionStatus.DISCONNECTED, detector.connection.value.status)
    }

    @Test
    fun `blank host is rejected without network access`() = runBlocking {
        val detector = ConsoleDetector(ConsoleClientFactory(NoOpLogger), NoOpLogger)
        val result = detector.detect(ConsoleConfig(host = ""))
        assertFalse(result.reachable)
        assertTrue(result.message.contains("IP"))
    }
}
