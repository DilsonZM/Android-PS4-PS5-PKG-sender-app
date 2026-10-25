package com.remotesend.app.domain

import com.remotesend.app.domain.model.BackupPackage
import com.remotesend.app.domain.model.BackupPayload
import com.remotesend.app.domain.model.BackupQueueItem
import com.remotesend.app.domain.model.QueueStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPayloadTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `serializes and deserializes a backup`() {
        val payload = BackupPayload(
            exportedAt = 123L,
            appVersion = "1.0.0",
            packages = listOf(
                BackupPackage(
                    displayName = "Game.pkg",
                    safeName = "Game.pkg",
                    title = "Game",
                    titleId = "CUSA00001",
                    sizeBytes = 42,
                ),
            ),
            queue = listOf(
                BackupQueueItem(
                    displayName = "Game.pkg",
                    safeName = "Game.pkg",
                    title = "Game",
                    status = QueueStatus.QUEUED,
                ),
            ),
        )

        val encoded = json.encodeToString(BackupPayload.serializer(), payload)
        val decoded = json.decodeFromString(BackupPayload.serializer(), encoded)

        assertEquals(1, decoded.packages.size)
        assertEquals("CUSA00001", decoded.packages.first().titleId)
        assertEquals(QueueStatus.QUEUED, decoded.queue.first().status)
        assertTrue(encoded.contains("\"schemaVersion\":1"))
    }

    @Test
    fun `ignores unknown keys for future compatibility`() {
        val payload = json.decodeFromString(
            BackupPayload.serializer(),
            """{"schemaVersion":2,"exportedAt":1,"appVersion":"x","future":true,"packages":[],"queue":[]}""",
        )
        assertEquals(2, payload.schemaVersion)
    }
}
