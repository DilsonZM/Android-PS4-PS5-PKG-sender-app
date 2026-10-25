package com.remotesend.app.data.repository

import com.remotesend.app.data.persist.StateStore
import com.remotesend.app.domain.model.AddToQueueResult
import com.remotesend.app.domain.model.PkgFile
import com.remotesend.app.domain.model.QueueItem
import com.remotesend.app.domain.model.QueueStatus
import com.remotesend.app.domain.model.isTerminal
import com.remotesend.app.domain.util.QueueOps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class QueueRepository(
    private val stateStore: StateStore,
) {
    val queue: Flow<List<QueueItem>> =
        stateStore.state.map { it.queue }.distinctUntilChanged()

    suspend fun snapshot(): List<QueueItem> = stateStore.snapshot().queue

    suspend fun find(id: String): QueueItem? =
        stateStore.snapshot().queue.firstOrNull { it.id == id }

    suspend fun add(pkg: PkgFile): AddToQueueResult {
        var result = AddToQueueResult(false, "No se pudo añadir a la cola")
        val item = QueueItem(
            id = UUID.randomUUID().toString(),
            pkgId = pkg.id,
            displayName = pkg.displayName,
            title = pkg.effectiveTitle,
            titleId = pkg.titleId,
            category = pkg.category ?: com.remotesend.app.domain.util.PkgNaming
                .inferCategory(pkg.displayName),
            appVersion = pkg.appVersion,
            iconPath = pkg.iconPath,
            sizeBytes = pkg.sizeBytes,
            status = QueueStatus.QUEUED,
            addedAt = System.currentTimeMillis(),
        )

        stateStore.update { state ->
            val duplicate = state.queue.any {
                it.pkgId == pkg.id && !it.status.isTerminal
            }
            if (duplicate) {
                result = AddToQueueResult(false, "El archivo ya está en la cola")
                state
            } else {
                result = AddToQueueResult(true)
                state.copy(queue = state.queue + item)
            }
        }
        return result
    }

    suspend fun remove(id: String) {
        stateStore.update { state ->
            state.copy(queue = state.queue.filterNot { it.id == id })
        }
    }

    suspend fun removeByPkg(pkgId: String) {
        stateStore.update { state ->
            state.copy(queue = state.queue.filterNot { it.pkgId == pkgId })
        }
    }

    suspend fun update(id: String, transform: (QueueItem) -> QueueItem) {
        stateStore.update { state ->
            state.copy(queue = state.queue.map { if (it.id == id) transform(it) else it })
        }
    }

    suspend fun move(id: String, delta: Int) {
        stateStore.update { state ->
            state.copy(queue = QueueOps.move(state.queue, id, delta))
        }
    }

    suspend fun clearFinished() {
        stateStore.update { state ->
            state.copy(queue = QueueOps.removeFinished(state.queue))
        }
    }

    suspend fun reset() {
        stateStore.update { it.copy(queue = emptyList()) }
    }

    suspend fun setStatus(
        id: String,
        status: QueueStatus,
        message: String? = null,
    ) {
        update(id) {
            it.copy(
                status = status,
                message = message ?: it.message,
                finishedAt = if (status.isTerminal) System.currentTimeMillis() else it.finishedAt,
            )
        }
    }

    suspend fun setTask(id: String, taskId: Long?) {
        update(id) { it.copy(taskId = taskId) }
    }

    suspend fun setProgress(
        id: String,
        progress: Int,
        restSeconds: Long,
        transferredBytes: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long,
    ) {
        update(id) {
            it.copy(
                progress = progress.coerceIn(0, 100),
                restSeconds = restSeconds.coerceAtLeast(0),
                transferredBytes = transferredBytes.coerceAtLeast(0),
                totalBytes = totalBytes.coerceAtLeast(0),
                speedBytesPerSecond = speedBytesPerSecond.coerceAtLeast(0),
            )
        }
    }

    suspend fun markStarted(
        id: String,
        status: QueueStatus,
        taskId: Long?,
        consoleType: com.remotesend.app.domain.model.ConsoleType,
        serverUrl: String,
    ) {
        update(id) {
            it.copy(
                status = status,
                taskId = taskId,
                consoleType = consoleType,
                serverUrl = serverUrl,
                progress = 0,
                restSeconds = 0,
                transferredBytes = 0,
                totalBytes = 0,
                speedBytesPerSecond = 0,
                startedAt = System.currentTimeMillis(),
                finishedAt = null,
                message = null,
            )
        }
    }

    suspend fun markFinished(
        id: String,
        status: QueueStatus,
        message: String? = null,
        progress: Int? = null,
    ) {
        update(id) {
            it.copy(
                status = status,
                message = message,
                finishedAt = System.currentTimeMillis(),
                progress = progress ?: it.progress,
                restSeconds = 0,
                speedBytesPerSecond = 0,
            )
        }
    }

    suspend fun nextQueued(excludeId: String? = null): QueueItem? =
        QueueOps.nextQueued(stateStore.snapshot().queue, excludeId)

    suspend fun hasActiveInstall(): Boolean =
        stateStore.snapshot().queue.any { it.status == QueueStatus.INSTALLING }
}
