package com.remotesend.app.data.network

import com.remotesend.app.core.NoOpLogger
import com.remotesend.app.domain.model.ConsoleType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

class LanScannerTest {

    @Test
    fun `scanner detects RPI on a configured custom port`() = runBlocking {
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.2"))
        try {
            val results = LanScanner(NoOpLogger).scan(
                localIp = "127.0.0.1",
                timeoutMs = 40,
                rpiPort = server.localPort,
                tcpPort = 9090,
            ).toList()

            val candidate = results.mapNotNull { it.candidate }
                .firstOrNull { it.host == "127.0.0.2" }
            assertTrue("No se detectó 127.0.0.2 en $results", candidate != null)
            assertEquals(ConsoleType.PS4_RPI, candidate?.suggestedType)
            assertEquals(server.localPort, candidate?.rpiPort)
        } finally {
            server.close()
        }
    }
}
