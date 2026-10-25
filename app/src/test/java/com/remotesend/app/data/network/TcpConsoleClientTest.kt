package com.remotesend.app.data.network

import com.remotesend.app.core.NoOpLogger
import com.remotesend.app.domain.model.ConsoleConfig
import com.remotesend.app.domain.model.ConsoleInstallResult
import com.remotesend.app.domain.model.ConsoleType
import com.remotesend.app.domain.model.PkgFile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class TcpConsoleClientTest {

    @Test
    fun `ps5 install sends url payload`() = runBlocking {
        withServer("""{"res":0}""") { port, requests ->
            val client = client(ConsoleType.PS5_ETAHEN, port)
            val result = client.install(pkg(), "http://phone:8337/pkg/abc/file.pkg", null)

            assertTrue(result is ConsoleInstallResult.Accepted)
            val payload = Json.parseToJsonElement(requests.first()).jsonObject
            assertEquals(
                "http://phone:8337/pkg/abc/file.pkg",
                payload["url"]?.jsonPrimitive?.content,
            )
        }
    }

    @Test
    fun `goldhen install sends content metadata`() = runBlocking {
        withServer("""{"res":0}""") { port, requests ->
            val client = client(ConsoleType.PS4_GOLDHEN, port)
            val result = client.install(
                pkg(),
                "http://phone:8337/pkg/abc/file.pkg",
                "http://phone:8337/icon/abc",
            )

            assertTrue(result is ConsoleInstallResult.Accepted)
            val payload = Json.parseToJsonElement(requests.first()).jsonObject
            assertEquals("UP0001-CUSA09311_00-TEST0000000001", payload["id"]?.jsonPrimitive?.content)
            assertEquals("Test Game", payload["contentName"]?.jsonPrimitive?.content)
            assertEquals(
                "http://phone:8337/icon/abc",
                payload["iconPath"]?.jsonPrimitive?.content,
            )
        }
    }

    @Test
    fun `non zero result maps to failure`() = runBlocking {
        withServer("""{"res":-2135809020}""") { port, _ ->
            val result = client(ConsoleType.PS5_ETAHEN, port)
                .install(pkg(), "http://phone:8337/pkg/abc/file.pkg", null)
            assertTrue(result is ConsoleInstallResult.Failure)
        }
    }

    @Test
    fun `connection refused is reported`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val health = client(ConsoleType.PS5_ETAHEN, port).checkConnection()
        assertTrue(!health.reachable)
    }

    private fun client(type: ConsoleType, port: Int): TcpConsoleClient = TcpConsoleClient(
        config = ConsoleConfig(
            type = type,
            host = "127.0.0.1",
            tcpPort = port,
            timeoutMs = 1_500,
        ),
        type = type,
        logger = NoOpLogger,
    )

    private fun withServer(
        response: String,
        block: suspend (Int, List<String>) -> Unit,
    ) = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val requests = CopyOnWriteArrayList<String>()
        val worker = thread(name = "tcp-test-server") {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 3_000
                    val input = socket.getInputStream()
                    val buffer = ByteArrayOutputStream()
                    while (true) {
                        val value = input.read()
                        if (value < 0) break
                        buffer.write(value)
                        if (buffer.toString(Charsets.UTF_8.name()).trim().endsWith("}")) break
                    }
                    requests += buffer.toString(Charsets.UTF_8.name())
                    socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                    Thread.sleep(80)
                }
            } catch (_: Exception) {
                // Test server shutdown.
            }
        }

        try {
            block(server.localPort, requests)
        } finally {
            runCatching { server.close() }
            worker.join(2_000)
        }
    }

    private fun pkg(): PkgFile = PkgFile(
        id = "abc",
        uri = "content://test/abc",
        displayName = "UP0001-CUSA09311_00-TEST.pkg",
        safeName = "UP0001-CUSA09311_00-TEST.pkg",
        sizeBytes = 100,
        title = "Test Game",
        titleId = "CUSA09311",
        contentId = "UP0001-CUSA09311_00-TEST0000000001",
    )
}
