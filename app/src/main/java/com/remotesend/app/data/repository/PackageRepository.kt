package com.remotesend.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.remotesend.app.core.AppLogger
import com.remotesend.app.data.persist.StateStore
import com.remotesend.app.data.pkg.skipFully
import com.remotesend.app.domain.model.PkgFile
import com.remotesend.app.domain.model.PkgMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import kotlinx.coroutines.withContext

class PackageRepository(
    private val context: Context,
    private val stateStore: StateStore,
    private val logger: AppLogger,
) {
    private val resolver: ContentResolver = context.contentResolver

    val packages: Flow<List<PkgFile>> =
        stateStore.state.map { it.packages }.distinctUntilChanged()

    suspend fun snapshot(): List<PkgFile> = stateStore.snapshot().packages

    suspend fun find(id: String): PkgFile? =
        stateStore.snapshot().packages.firstOrNull { it.id == id }

    suspend fun addAll(files: List<PkgFile>): Int {
        if (files.isEmpty()) return 0
        var added = 0
        stateStore.update { state ->
            val existingUris = state.packages.map { it.uri }.toSet()
            val toAdd = files.filterNot { it.uri in existingUris }
            added = toAdd.size
            state.copy(packages = state.packages + toAdd)
        }
        return added
    }

    suspend fun updateMetadata(id: String, metadata: PkgMetadata, iconPath: String?) {
        stateStore.update { state ->
            state.copy(
                packages = state.packages.map { pkg ->
                    if (pkg.id != id) pkg else pkg.copy(
                        title = metadata.title ?: pkg.title,
                        titleId = metadata.titleId ?: pkg.titleId,
                        contentId = metadata.contentId ?: pkg.contentId,
                        appVersion = metadata.appVersion ?: pkg.appVersion,
                        category = metadata.category ?: pkg.category,
                        iconPath = iconPath ?: pkg.iconPath,
                        metadataRead = true,
                    )
                },
            )
        }
    }

    suspend fun remove(id: String) {
        stateStore.update { state ->
            state.copy(packages = state.packages.filterNot { it.id == id })
        }
    }

    fun openStreamAt(pkg: PkgFile, offset: Long): InputStream? {
        val uri = Uri.parse(pkg.uri)
        if (uri.scheme == "file") {
            return openFileStream(uri.path, offset)
        }
        return try {
            val descriptor = resolver.openFileDescriptor(uri, "r")
            if (descriptor != null) {
                val stream = FileInputStream(descriptor.fileDescriptor)
                try {
                    stream.channel.position(offset.coerceAtLeast(0L))
                    CloseDescriptorStream(stream, descriptor)
                } catch (_: Exception) {
                    runCatching { stream.close() }
                    runCatching { descriptor.close() }
                    fallbackStream(uri, offset)
                }
            } else {
                fallbackStream(uri, offset)
            }
        } catch (exception: Exception) {
            logger.warn("Servidor", "No se pudo abrir ${pkg.displayName}", exception.message)
            null
        }
    }

    private fun openFileStream(path: String?, offset: Long): InputStream? {
        if (path.isNullOrBlank()) return null
        return try {
            val stream = FileInputStream(File(path))
            stream.channel.position(offset.coerceAtLeast(0L))
            stream
        } catch (exception: Exception) {
            logger.warn("Servidor", "No se pudo abrir el archivo local", exception.message)
            null
        }
    }

    private fun fallbackStream(uri: Uri, offset: Long): InputStream? {
        val input = resolver.openInputStream(uri) ?: return null
        return try {
            input.skipFully(offset)
            input
        } catch (exception: Exception) {
            runCatching { input.close() }
            logger.warn("Servidor", "No se pudo posicionar el stream", exception.message)
            null
        }
    }

    fun readIcon(pkg: PkgFile): ByteArray? {
        val path = pkg.iconPath ?: return null
        val file = File(path)
        if (!file.isFile) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    fun iconFile(pkgId: String): File {
        val directory = File(context.filesDir, "pkg_icons")
        if (!directory.exists()) directory.mkdirs()
        return File(directory, "$pkgId.png")
    }

    suspend fun deleteFile(pkg: PkgFile): Result<Unit> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(pkg.uri)
        try {
            if (uri.scheme == "file") {
                val file = uri.path?.let(::File)
                if (file != null && file.exists() && !file.delete()) {
                    return@withContext Result.failure(
                        IOException("No se pudo borrar ${file.name}"),
                    )
                }
            } else {
                val document = DocumentFile.fromSingleUri(context, uri)
                val deletedByDocument = runCatching { document?.delete() == true }
                    .getOrDefault(false)
                val deletedByResolver = if (!deletedByDocument) {
                    runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                        .getOrDefault(false)
                } else {
                    true
                }
                if (!deletedByResolver) {
                    return@withContext Result.failure(
                        IOException(
                            "Android no permitió borrar el archivo. " +
                                "Concede acceso total o vuelve a importarlo con escritura.",
                        ),
                    )
                }
            }

            pkg.iconPath?.let { icon -> runCatching { File(icon).delete() } }
            Result.success(Unit)
        } catch (exception: Exception) {
            logger.warn("Biblioteca", "No se pudo eliminar ${pkg.displayName}", exception.message)
            Result.failure(exception)
        }
    }

    suspend fun exists(pkg: PkgFile): Boolean {
        val uri = Uri.parse(pkg.uri)
        if (uri.scheme == "file") {
            val file = uri.path?.let(::File) ?: return false
            return file.isFile && file.canRead()
        }
        return try {
            resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private class CloseDescriptorStream(
        input: FileInputStream,
        private val descriptor: ParcelFileDescriptor,
    ) : FilterInputStream(input) {
        override fun close() {
            runCatching { super.close() }
            runCatching { descriptor.close() }
        }
    }
}
