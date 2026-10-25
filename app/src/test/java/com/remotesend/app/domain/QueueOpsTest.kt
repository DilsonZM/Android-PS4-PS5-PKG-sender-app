package com.remotesend.app.domain

import com.remotesend.app.domain.model.QueueItem
import com.remotesend.app.domain.model.QueueStatus
import com.remotesend.app.domain.util.QueueOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueOpsTest {

    @Test
    fun `moves an item up and down`() {
        val items = listOf(item("a"), item("b"), item("c"))

        assertEquals(listOf("b", "a", "c"), QueueOps.move(items, "a", 1).map { it.id })
        assertEquals(listOf("a", "c", "b"), QueueOps.move(items, "b", 1).map { it.id })
        assertEquals(listOf("a", "c", "b"), QueueOps.move(items, "c", -1).map { it.id })
    }

    @Test
    fun `does not move past boundaries`() {
        val items = listOf(item("a"), item("b"))
        assertEquals(listOf("a", "b"), QueueOps.move(items, "a", -1).map { it.id })
        assertEquals(listOf("a", "b"), QueueOps.move(items, "b", 1).map { it.id })
    }

    @Test
    fun `next queued skips terminal items and the excluded id`() {
        val items = listOf(
            item("a", QueueStatus.COMPLETED),
            item("b", QueueStatus.QUEUED),
            item("c", QueueStatus.QUEUED),
        )
        assertEquals("b", QueueOps.nextQueued(items)?.id)
        assertEquals("c", QueueOps.nextQueued(items, excludeId = "b")?.id)
    }

    @Test
    fun `next queued returns null when empty`() {
        assertNull(QueueOps.nextQueued(emptyList()))
    }

    @Test
    fun `remove finished keeps active items only`() {
        val items = listOf(
            item("a", QueueStatus.QUEUED),
            item("b", QueueStatus.COMPLETED),
            item("c", QueueStatus.FAILED),
            item("d", QueueStatus.INSTALLING),
            item("e", QueueStatus.SENT),
            item("f", QueueStatus.EXISTS),
        )
        assertEquals(listOf("a", "d"), QueueOps.removeFinished(items).map { it.id })
    }

    private fun item(id: String, status: QueueStatus = QueueStatus.QUEUED) = QueueItem(
        id = id,
        pkgId = "pkg-$id",
        displayName = "$id.pkg",
        status = status,
    )
}
