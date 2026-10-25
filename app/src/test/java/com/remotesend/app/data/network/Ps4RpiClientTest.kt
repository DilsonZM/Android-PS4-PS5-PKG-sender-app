package com.remotesend.app.data.network

import com.remotesend.app.core.NoOpLogger
import com.remotesend.app.domain.model.ConsoleConfig
import com.remotesend.app.domain.model.ConsoleInstallResult
import com.remotesend.app.domain.model.PkgFile
import com.remotesend.app.domain.model.TaskProgress
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Ps4RpiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: Ps4RpiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = Ps4RpiClient(
            config = ConsoleConfig(
                type = com.remotesend.app.domain.model.ConsoleType.PS4_RPI,
                host = server.hostName,
                httpPort = server.port,
                timeoutMs = 2_000,
            ),
            logger = NoOpLogger,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `install success returns task id`() = runBlocking {
        server.enqueue(json("""{"status":"success","task_id":268435806,"title":"Game"}"""))

        val result = client.install(pkg(), "http://phone:8337/pkg/abc/file.pkg", null)

        assertTrue(result is ConsoleInstallResult.Accepted)
        assertEquals(268435806L, (result as ConsoleInstallResult.Accepted).taskId)
        val request = server.takeRequest()
        assertEquals("/api/install", request.path)
        val requestBody = request.body.readUtf8()
        assertTrue(requestBody.contains("\"type\":\"direct\""))
        assertTrue(requestBody.contains("\"packages\""))
    }

    @Test
    fun `install already installed maps error code`() = runBlocking {
        server.enqueue(json("""{"status":"fail","error_code":2157510677}"""))

        val result = client.install(pkg(), "http://phone:8337/pkg/abc/file.pkg", null)

        assertTrue(result is ConsoleInstallResult.AlreadyInstalled)
    }

    @Test
    fun `task progress parses byte counters`() = runBlocking {
        server.enqueue(
            json(
                """{"status":"success","length":1000,"transferred":250,"length_total":1000,"transferred_total":250,"rest_sec_total":30}""",
            ),
        )

        val progress = client.taskProgress(12L)

        assertTrue(progress is TaskProgress.Running)
        progress as TaskProgress.Running
        assertEquals(25, progress.percent)
        assertEquals(30L, progress.restSeconds)
    }

    @Test
    fun `task progress uses length when totals are zero`() = runBlocking {
        server.enqueue(
            json(
                """{"status":"success","length":1000,"transferred":250,"length_total":0,"transferred_total":0,"rest_sec_total":30}""",
            ),
        )

        val progress = client.taskProgress(12L) as TaskProgress.Running
        assertEquals(25, progress.percent)
        assertEquals(250L, progress.transferredBytes)
        assertEquals(1000L, progress.totalBytes)
    }

    @Test
    fun `task progress uses transferred totals when present`() = runBlocking {
        server.enqueue(
            json(
                """{"status":"success","length":0,"transferred":0,"length_total":2000,"transferred_total":1500,"rest_sec_total":10}""",
            ),
        )

        val progress = client.taskProgress(12L) as TaskProgress.Running
        assertEquals(75, progress.percent)
        assertEquals(1500L, progress.transferredBytes)
        assertEquals(2000L, progress.totalBytes)
    }

    @Test
    fun `task progress parses hexadecimal byte counters`() = runBlocking {
        server.enqueue(
            json(
                """{"status":"success","length":"0x7B440000","transferred":"0x29CE0000","length_total":"0x7B440000","transferred_total":"0x29CE0000","rest_sec_total":123}""",
            ),
        )

        val progress = client.taskProgress(12L) as TaskProgress.Running
        assertEquals(2068054016L, progress.totalBytes)
        assertEquals(701366272L, progress.transferredBytes)
        assertEquals(33, progress.percent)
        assertEquals(123L, progress.restSeconds)
    }

    @Test
    fun `hexadecimal error codes map to task missing`() = runBlocking {
        server.enqueue(json("""{"status":"fail","error_code":"0x80990019"}"""))
        assertTrue(client.taskProgress(12L) is TaskProgress.TaskMissing)
    }

    @Test
    fun `task progress reports completion`() = runBlocking {
        server.enqueue(
            json("""{"status":"success","length":1000,"transferred":1000,"rest_sec_total":0}"""),
        )

        assertTrue(client.taskProgress(12L) is TaskProgress.Completed)
    }

    @Test
    fun `missing task maps to task missing`() = runBlocking {
        server.enqueue(json("""{"status":"fail","error_code":2157510681}"""))

        assertTrue(client.taskProgress(12L) is TaskProgress.TaskMissing)
    }

    @Test
    fun `is installed parses exists and hexadecimal size`() = runBlocking {
        server.enqueue(
            json("""{"status":"success","exists":"true","size":"0x7A000"}"""),
        )

        val status = client.isInstalled("CUSA29952")

        assertTrue(status?.exists == true)
        assertEquals(499712L, status?.sizeBytes)
        val request = server.takeRequest()
        assertEquals("/api/is_exists", request.path)
        assertTrue(request.body.readUtf8().contains("\"title_id\":\"CUSA29952\""))
    }

    @Test
    fun `uninstall game posts title id`() = runBlocking {
        server.enqueue(json("""{"status":"success"}"""))

        assertTrue(client.uninstallGame("CUSA29952"))

        val request = server.takeRequest()
        assertEquals("/api/uninstall_game", request.path)
        assertTrue(request.body.readUtf8().contains("\"title_id\":\"CUSA29952\""))
    }

    @Test
    fun `uninstall update and dlc use their endpoints`() = runBlocking {
        server.enqueue(json("""{"status":"success"}"""))
        assertTrue(client.uninstallPatch("CUSA29952"))
        assertEquals("/api/uninstall_patch", server.takeRequest().path)

        server.enqueue(json("""{"status":"success"}"""))
        assertTrue(client.uninstallAc("UP3456-CUSA29952_00-1412781209801863"))
        val dlcRequest = server.takeRequest()
        assertEquals("/api/uninstall_ac", dlcRequest.path)
        assertTrue(
            dlcRequest.body.readUtf8()
                .contains("\"content_id\":\"UP3456-CUSA29952_00-1412781209801863\""),
        )
    }

    @Test
    fun `check connection succeeds with valid RPI response`() = runBlocking {
        server.enqueue(json("""{"status":"success","exists":false}"""))
        assertTrue(client.checkConnection().reachable)
    }

    @Test
    fun `check connection fails on network error`() = runBlocking {
        server.shutdown()
        val offline = Ps4RpiClient(
            config = ConsoleConfig(host = "127.0.0.1", httpPort = 1, timeoutMs = 300),
            logger = NoOpLogger,
        )
        assertTrue(!offline.checkConnection().reachable)
    }

    private fun json(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun pkg(): PkgFile = PkgFile(
        id = "abc",
        uri = "content://test/abc",
        displayName = "UP0001-CUSA09311_00-TEST.pkg",
        safeName = "UP0001-CUSA09311_00-TEST.pkg",
        sizeBytes = 100,
        title = "Test",
        titleId = "CUSA09311",
        contentId = "UP0001-CUSA09311_00-TEST0000000001",
    )
}
