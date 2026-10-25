package com.remotesend.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.remotesend.app.core.AppLogger
import com.remotesend.app.data.persist.StateStore
import com.remotesend.app.data.pkg.ContentUriSeekableSource
import com.remotesend.app.data.pkg.FileSeekableSource
import com.remotesend.app.data.pkg.PkgMetadataReader
import com.remotesend.app.domain.model.ImportResult
import com.remotesend.app.domain.model.PkgFile
import com.remotesend.app.domain.model.PkgMetadata
import com.remotesend.app.domain.util.PkgNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

class PackageImporter(
    private val context: Context,
    private val stateStore: StateStore,
    private val metadataReader: PkgMetadataReader,
    private val logger: AppLogger,
) {
    private val resolver = context.contentResolver
    private val importMutex = Mutex()

    suspend fun importUris(
        uris: List<Uri>,
        readMetadata: Boolean,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): ImportResult = importMutex.withLock {
        importUrisInternal(uris, readMetadata, onProgress)
    }

    private suspend fun importUrisInternal(
        uris: List<Uri>,
        readMetadata: Boolean,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): ImportResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext ImportResult()

        val messages = mutableListOf<String>()
        var skipped = 0
        var failed = 0
        val candidates = mutableListOf<PkgFile>()
        val existing = stateStore.snapshot().packages
        val existingByUri = existing.associateBy { it.uri }

        uris.forEachIndexed { index, uri ->
            coroutineContext.ensureActive()
            onProgress?.invoke(index + 1, uris.size)
            try {
                persistPermission(uri)
                val (displayName, size) = queryNameAndSize(uri)
                if (!PkgNaming.isPkgName(displayName)) {
                    skipped++
                    messages += "$displayName no es un archivo .pkg"
                    return@forEachIndexed
                }

                val id = PkgNaming.pkgId(uri.toString())
                val previous = existingByUri[uri.toString()]
                if (previous != null) {
                    if (!previous.metadataRead && readMetadata) {
                        val refreshed = readMetadata(uri, id, displayName)
                        if (refreshed != null) {
                            candidates += previous.copy(
                                title = refreshed.title ?: previous.title,
                                titleId = refreshed.titleId ?: previous.titleId,
                                contentId = refreshed.contentId ?: previous.contentId,
                                appVersion = refreshed.appVersion ?: previous.appVersion,
                                category = refreshed.category ?: previous.category,
                                iconPath = refreshed.iconPath ?: previous.iconPath,
                                metadataRead = true,
                            )
                            skipped++
                            return@forEachIndexed
                        }
                    }
                    skipped++
                    return@forEachIndexed
                }

                val built = buildPkgFile(uri, id, displayName, size, readMetadata)
                candidates += built
            } catch (exception: Exception) {
                failed++
                messages += "${uri.lastPathSegment ?: uri}: ${exception.message ?: "error"}"
            }
        }

        if (candidates.isEmpty()) {
            return@withContext ImportResult(0, skipped, failed, messages.take(6))
        }

        val candidateByUri = candidates.associateBy { it.uri }
        var added = 0
        stateStore.update { state ->
            val updatedPackages = state.packages.map { pkg ->
                candidateByUri[pkg.uri]?.takeIf { !pkg.metadataRead }?.let { fresh ->
                    pkg.copy(
                        title = fresh.title ?: pkg.title,
                        titleId = fresh.titleId ?: pkg.titleId,
                        contentId = fresh.contentId ?: pkg.contentId,
                        appVersion = fresh.appVersion ?: pkg.appVersion,
                        category = fresh.category ?: pkg.category,
                        iconPath = fresh.iconPath ?: pkg.iconPath,
                        metadataRead = fresh.metadataRead,
                    )
                } ?: pkg
            }
            val knownUris = updatedPackages.map { it.uri }.toSet()
            val newItems = candidates.filterNot { it.uri in knownUris }
            added = newItems.size
            state.copy(packages = updatedPackages + newItems)
        }

        logger.success(
            "Importación",
            "Se importaron $added PKG (${skipped} omitidos, $failed con error)",
        )

        ImportResult(
            added = added,
            skipped = skipped,
            failed = failed,
            messages = messages.take(6),
        )
    }

    suspend fun importTree(
        treeUri: Uri,
        scanSubdirectories: Boolean,
        readMetadata: Boolean,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): ImportResult = importMutex.withLock {
        importTreeInternal(treeUri, scanSubdirectories, readMetadata, onProgress)
    }

    private suspend fun importTreeInternal(
        treeUri: Uri,
        scanSubdirectories: Boolean,
        readMetadata: Boolean,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): ImportResult = withContext(Dispatchers.IO) {
        persistPermission(treeUri)
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext ImportResult(failed = 1, messages = listOf("No se pudo abrir la carpeta"))

        val pkgUris = mutableListOf<Uri>()
        collectPkgFiles(root, scanSubdirectories, pkgUris)

        logger.info("Importación", "Encontrados ${pkgUris.size} PKG en la carpeta")
        if (pkgUris.isEmpty()) {
            return@withContext ImportResult(skipped = 0)
        }
        importUrisInternal(pkgUris, readMetadata, onProgress)
    }

    suspend fun importFromPath(
        rootPath: String,
        scanSubdirectories: Boolean,
        readMetadata: Boolean,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): ImportResult = importMutex.withLock {
        importFromPathInternal(rootPath, scanSubdirectories, readMetadata, onProgress)
    }

    private suspend fun importFromPathInternal(
        rootPath: String,
        scanSubdirectories: Boolean,
        readMetadata: Boolean,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): ImportResult = withContext(Dispatchers.IO) {
        val root = File(rootPath)
        if (!root.isDirectory || !root.canRead()) {
            return@withContext ImportResult(
                failed = 1,
                messages = listOf("No se puede leer la ruta $rootPath"),
            )
        }

        val pkgFiles = mutableListOf<File>()
        collectFiles(root, scanSubdirectories, pkgFiles)
        logger.info("Escaneo", "Encontrados ${pkgFiles.size} PKG en $rootPath")
        if (pkgFiles.isEmpty()) {
            return@withContext ImportResult(
                messages = listOf("No hay archivos .pkg en ${root.name}"),
            )
        }

        val existing = stateStore.snapshot().packages
        val knownUris = existing.map { it.uri }.toSet()
        val knownNames = existing.map { it.displayName.lowercase() to it.sizeBytes }.toSet()
        val newItems = mutableListOf<PkgFile>()
        var skipped = 0
        var failed = 0
        val messages = mutableListOf<String>()

        pkgFiles.forEachIndexed { index, file ->
            coroutineContext.ensureActive()
            onProgress?.invoke(index + 1, pkgFiles.size)
            try {
                val uriString = Uri.fromFile(file).toString()
                val size = file.length()
                if (uriString in knownUris ||
                    (file.name.lowercase() to size) in knownNames
                ) {
                    skipped++
                    return@forEachIndexed
                }
                val id = PkgNaming.pkgId(uriString)
                val metadata = if (readMetadata) readMetadataFromFile(file, id, file.name) else null
                val titleId = metadata?.titleId
                    ?: PkgNaming.titleIdFromName(file.name)
                    ?: PkgNaming.titleIdFromContentId(metadata?.contentId)
                val contentId = metadata?.contentId ?: PkgNaming.contentIdFromName(file.name)
                newItems += PkgFile(
                    id = id,
                    uri = uriString,
                    displayName = file.name,
                    safeName = PkgNaming.safeFileName(file.name),
                    sizeBytes = size,
                    title = metadata?.title,
                    titleId = titleId,
                    contentId = contentId,
                    appVersion = metadata?.appVersion,
                    category = metadata?.category,
                    iconPath = metadata?.iconPath,
                    metadataRead = metadata != null,
                    addedAt = System.currentTimeMillis(),
                )
            } catch (exception: Exception) {
                failed++
                messages += "${file.name}: ${exception.message ?: "error"}"
            }
        }

        var added = 0
        if (newItems.isNotEmpty()) {
            val candidateByUri = newItems.associateBy { it.uri }
            stateStore.update { state ->
                val stillKnown = state.packages.map { it.uri }.toSet()
                val additions = candidateByUri.values.filterNot { it.uri in stillKnown }
                added = additions.size
                state.copy(packages = state.packages + additions)
            }
        }

        if (added > 0) {
            logger.success(
                "Escaneo",
                "Se importaron $added PKG desde $rootPath ($skipped omitidos, $failed fallidos)",
            )
        }
        ImportResult(added, skipped, failed, messages.take(6))
    }

    suspend fun refreshMetadata(pkg: PkgFile): PkgFile? = withContext(Dispatchers.IO) {
        try {
            persistPermission(Uri.parse(pkg.uri))
            val refreshed = readMetadata(Uri.parse(pkg.uri), pkg.id, pkg.displayName) ?: return@withContext null
            stateStore.update { state ->
                state.copy(
                    packages = state.packages.map {
                        if (it.id == pkg.id) {
                            it.copy(
                                title = refreshed.title ?: it.title,
                                titleId = refreshed.titleId ?: it.titleId,
                                contentId = refreshed.contentId ?: it.contentId,
                                appVersion = refreshed.appVersion ?: it.appVersion,
                                category = refreshed.category ?: it.category,
                                iconPath = refreshed.iconPath ?: it.iconPath,
                                metadataRead = true,
                            )
                        } else {
                            it
                        }
                    },
                )
            }
            pkg.copy(metadataRead = true)
        } catch (exception: Exception) {
            logger.warn("Importación", "No se pudo leer ${pkg.displayName}", exception.message)
            null
        }
    }

    private suspend fun buildPkgFile(
        uri: Uri,
        id: String,
        displayName: String,
        size: Long,
        readMetadata: Boolean,
    ): PkgFile {
        val metadata = if (readMetadata) readMetadata(uri, id, displayName) else null
        val titleId = metadata?.titleId
            ?: PkgNaming.titleIdFromName(displayName)
            ?: PkgNaming.titleIdFromContentId(metadata?.contentId)
        val contentId = metadata?.contentId ?: PkgNaming.contentIdFromName(displayName)

        return PkgFile(
            id = id,
            uri = uri.toString(),
            displayName = displayName,
            safeName = PkgNaming.safeFileName(displayName),
            sizeBytes = size,
            title = metadata?.title,
            titleId = titleId,
            contentId = contentId,
            appVersion = metadata?.appVersion,
            category = metadata?.category,
            iconPath = metadata?.iconPath,
            metadataRead = metadata != null,
            addedAt = System.currentTimeMillis(),
        )
    }

    private fun readMetadata(uri: Uri, id: String, displayName: String): PkgFileMetadata? {
        return try {
            ContentUriSeekableSource(resolver, uri).use { source ->
                metadataFromSource(source, id, displayName)
            }
        } catch (exception: Exception) {
            logger.debug("PkgReader", "Sin metadatos para $displayName", exception.message)
            null
        }
    }

    private fun readMetadataFromFile(file: File, id: String, displayName: String): PkgFileMetadata? {
        return try {
            FileSeekableSource(file).use { source ->
                metadataFromSource(source, id, displayName)
            }
        } catch (exception: Exception) {
            logger.debug("PkgReader", "Sin metadatos para $displayName", exception.message)
            null
        }
    }

    private fun metadataFromSource(
        source: com.remotesend.app.data.pkg.SeekableByteSource,
        id: String,
        displayName: String,
    ): PkgFileMetadata? {
        val metadata: PkgMetadata = metadataReader.read(source) ?: return null
        val iconPath = metadata.icon?.let { bytes ->
            val file = File(context.filesDir, "pkg_icons").apply { mkdirs() }
                .resolve("$id.png")
            file.writeBytes(bytes)
            file.absolutePath
        }
        return PkgFileMetadata(
            title = metadata.title,
            titleId = metadata.titleId
                ?: PkgNaming.titleIdFromName(displayName)
                ?: PkgNaming.titleIdFromContentId(metadata.contentId),
            contentId = metadata.contentId ?: PkgNaming.contentIdFromName(displayName),
            appVersion = metadata.appVersion,
            category = metadata.category ?: PkgNaming.inferCategory(displayName),
            iconPath = iconPath,
        )
    }

    private fun collectFiles(
        directory: File,
        recursive: Boolean,
        output: MutableList<File>,
    ) {
        val children = directory.listFiles() ?: return
        for (child in children) {
            if (output.size >= MAX_TREE_FILES) return
            if (child.name.startsWith(".") || child.name in IGNORED_NAMES) continue
            if (child.isDirectory) {
                if (recursive) collectFiles(child, true, output)
            } else if (PkgNaming.isPkgName(child.name)) {
                output += child
            }
        }
    }

    private fun collectPkgFiles(
        directory: DocumentFile,
        recursive: Boolean,
        output: MutableList<Uri>,
    ) {
        val children = runCatching { directory.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            if (output.size >= MAX_TREE_FILES) return
            val name = child.name ?: continue
            if (name.startsWith(".") || name in IGNORED_NAMES) continue
            if (child.isDirectory) {
                if (recursive) collectPkgFiles(child, true, output)
            } else if (PkgNaming.isPkgName(name)) {
                output += child.uri
            }
        }
    }

    private fun persistPermission(uri: Uri) {
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            resolver.takePersistableUriPermission(uri, readWrite)
        }.recoverCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "package-${UUID.randomUUID().toString().take(8)}.pkg"
        var size = 0L

        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        name = cursor.getString(nameIndex)
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        }

        if (size <= 0L) {
            size = runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { it.statSize }.takeIf { it != null && it > 0 }
                    ?: 0L
            }.getOrDefault(0L)
        }

        if (size <= 0L) {
            size = runCatching { DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L }
                .getOrDefault(0L)
        }

        return name to size
    }

    private data class PkgFileMetadata(
        val title: String?,
        val titleId: String?,
        val contentId: String?,
        val appVersion: String?,
        val category: String?,
        val iconPath: String?,
    )

    companion object {
        private const val MAX_TREE_FILES = 10_000
        private val IGNORED_NAMES = setOf(
            "desktop.ini",
            "Thumbs.db",
            ".DS_Store",
            "\$RECYCLE.BIN",
            "System Volume Information",
        )
    }
}
