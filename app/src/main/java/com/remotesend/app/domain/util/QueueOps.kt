package com.remotesend.app.domain.util

import com.remotesend.app.domain.model.QueueItem
import com.remotesend.app.domain.model.QueueStatus
import com.remotesend.app.domain.model.isTerminal

object QueueOps {
    fun move(items: List<QueueItem>, id: String, delta: Int): List<QueueItem> {
        if (items.isEmpty()) return items
        val from = items.indexOfFirst { it.id == id }
        if (from < 0) return items
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (to == from) return items
        return items.toMutableList().apply {
            val item = removeAt(from)
            add(to, item)
        }
    }

    fun nextQueued(items: List<QueueItem>, excludeId: String? = null): QueueItem? =
        items.firstOrNull {
            it.status == QueueStatus.QUEUED && it.id != excludeId
        }

    fun hasActiveInstall(items: List<QueueItem>): Boolean =
        items.any { it.status == QueueStatus.INSTALLING }

    fun removeFinished(items: List<QueueItem>): List<QueueItem> =
        items.filterNot { it.status.isTerminal }
}
