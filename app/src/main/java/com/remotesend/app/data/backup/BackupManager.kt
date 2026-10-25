package com.remotesend.app.data.backup

import android.content.Context
import android.net.Uri
import com.remotesend.app.BuildConfig
import com.remotesend.app.core.AppLogger
import com.remotesend.app.data.persist.StateStore
import com.remotesend.app.domain.model.BackupPackage
import com.remotesend.app.domain.model.BackupPayload
import com.remotesend.app.domain.model.BackupQueueItem
import com.remotesend.app.domain.model.BackupResult
import com.remotesend.app.domain.model.QueueItem
import com.remotesend.app.domain.model.QueueStatus
import com.remotesend.app.domain.model.isTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

class BackupManager(
    private val context: Context,
    private val stateStore: StateStore,
    private val logger: AppLogger,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun exportTo(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val state = stateStore.snapshot()
            val payload = BackupPayload(
                exportedAt = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
                packages = state.packages.map { pkg ->
                    BackupPackage(
                        displayName = pkg.displayName,
                        safeName = pkg.safeName,
                        title = pkg.title,
                        titleId = pkg.titleId,
                        contentId = pkg.contentId,
                        sizeBytes = pkg.sizeBytes,
                    )
                },
                queue = state.queue.map { item ->
                    BackupQueueItem(
                        displayName = item.displayName,
                        safeName = state.packages.firstOrNull { it.id == item.pkgId }?.safeName
                            ?: item.displayName,
                        title = item.title,
                        status = item.status,
                    )
                },
            )
            val content = json.encodeToString(BackupPayload.serializer(), payload)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            } ?: return@withContext BackupResult(false, "No se pudo abrir el archivo de destino")
            logger.success("Backup", "Copia exportada con ${payload.queue.size} elementos de cola")
            BackupResult(
                success = true,
                message = "Copia exportada: ${payload.packages.size} PKG y ${payload.queue.size} en cola",
            )
        } catch (exception: Exception) {
            logger.error("Backup", "Error exportando la copia", exception.message)
            BackupResult(false, "Error exportando: ${exception.message}")
        }
    }

    suspend fun importFrom(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                readLimited(input, MAX_BACKUP_BYTES).decodeToString()
            } ?: return@withContext BackupResult(false, "No se pudo abrir el archivo")

            val payload = json.decodeFromString(BackupPayload.serializer(), text)
            var matched = 0
            var skipped = 0
            val missing = mutableListOf<String>()

            stateStore.update { state ->
                matched = 0
                skipped = 0
                missing.clear()
                val queue = state.queue.toMutableList()
                payload.queue.forEach { entry ->
                    val pkg = state.packages.firstOrNull {
                        it.safeName == entry.safeName || it.displayName == entry.displayName
                    }
                    if (pkg == null) {
                        missing += entry.displayName
                        return@forEach
                    }
                    val duplicate = queue.any { it.pkgId == pkg.id && !it.status.isTerminal }
                    if (duplicate) {
                        skipped++
                        return@forEach
                    }
                    queue += QueueItem(
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
                    matched++
                }
                state.copy(queue = queue)
            }

            val message = buildString {
                append("Copia importada: $matched añadidos")
                if (skipped > 0) append(", $skipped ya estaban en la cola")
                if (missing.isNotEmpty()) append(", ${missing.size} no encontrados")
            }
            if (matched > 0) {
                logger.success("Backup", message)
            } else {
                logger.warn("Backup", message)
            }
            BackupResult(
                success = true,
                message = message,
                matched = matched,
                skipped = skipped,
                missing = missing.take(10),
            )
        } catch (exception: Exception) {
            logger.error("Backup", "Error importando la copia", exception.message)
            BackupResult(false, "Error importando: ${exception.message}")
        }
    }

    private fun readLimited(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (total < maxBytes) {
            val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }

    companion object {
        private const val MAX_BACKUP_BYTES = 4 * 1024 * 1024
    }
}
