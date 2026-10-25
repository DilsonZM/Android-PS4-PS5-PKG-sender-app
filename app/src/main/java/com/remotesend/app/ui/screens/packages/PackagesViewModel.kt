package com.remotesend.app.ui.screens.packages

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotesend.app.core.StorageAccess
import com.remotesend.app.di.AppContainer
import com.remotesend.app.domain.model.PackageGroup
import com.remotesend.app.domain.model.PkgFile
import com.remotesend.app.domain.model.QueueItem
import com.remotesend.app.domain.model.QueueStatus
import com.remotesend.app.domain.model.ServerRuntime
import com.remotesend.app.domain.util.PkgNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PackagesUiState(
    val packages: List<PkgFile> = emptyList(),
    val allPackagesCount: Int = 0,
    val queue: List<QueueItem> = emptyList(),
    val query: String = "",
    val serverRuntime: ServerRuntime = ServerRuntime.Stopped,
    val importing: Boolean = false,
    val importStatus: String? = null,
    val scanFolderName: String? = null,
    val autoScanOnStartup: Boolean = true,
    val allFilesAccess: Boolean = false,
    val scanPath: String = "",
    val groups: List<PackageGroup> = emptyList(),
    val groupByTitleId: Boolean = true,
    val collapsedGroups: Set<String> = emptySet(),
)

private data class PackagesLocalState(
    val query: String = "",
    val importing: Boolean = false,
    val importStatus: String? = null,
    val scanFolderName: String? = null,
    val autoScanOnStartup: Boolean = true,
    val allFilesAccess: Boolean = false,
    val scanPath: String = "",
    val groups: List<PackageGroup> = emptyList(),
    val groupByTitleId: Boolean = true,
    val collapsedGroups: Set<String> = emptySet(),
)

class PackagesViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val local = MutableStateFlow(PackagesLocalState())
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<PackagesUiState> = combine(
        container.stateStore.state,
        container.serverManager.state,
        local,
    ) { state, server, localState ->
        val query = localState.query.trim()
        val filtered = if (query.isBlank()) {
            state.packages
        } else {
            state.packages.filter { pkg ->
                pkg.displayName.contains(query, ignoreCase = true) ||
                    (pkg.title?.contains(query, ignoreCase = true) == true) ||
                    (pkg.titleId?.contains(query, ignoreCase = true) == true) ||
                    (pkg.contentId?.contains(query, ignoreCase = true) == true)
            }
        }
        val allFilesAccess = StorageAccess.hasAllFilesAccess(container.appContext)
        val scanFolderName = if (allFilesAccess && state.server.scanPath.isNotBlank()) {
            File(state.server.scanPath).name.ifBlank { state.server.scanPath }
        } else {
            state.server.lastScanFolderName
        }
        val sortedPackages = filtered.sortedBy { it.effectiveTitle.lowercase() }
        PackagesUiState(
            packages = sortedPackages,
            allPackagesCount = state.packages.size,
            queue = state.queue,
            query = localState.query,
            serverRuntime = server,
            importing = localState.importing,
            importStatus = localState.importStatus,
            scanFolderName = scanFolderName,
            autoScanOnStartup = state.server.autoScanOnStartup,
            allFilesAccess = allFilesAccess,
            scanPath = state.server.scanPath,
            groups = if (state.settings.groupPackagesByTitleId) {
                groupPackages(sortedPackages)
            } else {
                emptyList()
            },
            groupByTitleId = state.settings.groupPackagesByTitleId,
            collapsedGroups = state.settings.collapsedCusaGroups,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PackagesUiState(),
    )

    fun setQuery(value: String) {
        local.value = local.value.copy(query = value)
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty() || local.value.importing) return
        viewModelScope.launch {
            local.value = local.value.copy(importing = true, importStatus = "Leyendo archivos…")
            try {
                val readMetadata = container.stateStore.snapshot().server.readMetadata
                val result = withContext(Dispatchers.IO) {
                    container.packageImporter.importUris(
                        uris = uris,
                        readMetadata = readMetadata,
                        onProgress = { done, total ->
                            local.value = local.value.copy(
                                importStatus = "Importando $done de $total…",
                            )
                        },
                    )
                }
                emit(
                    buildString {
                        append("${result.added} PKG añadidos")
                        if (result.skipped > 0) append(", ${result.skipped} omitidos")
                        if (result.failed > 0) append(", ${result.failed} con error")
                        if (result.messages.isNotEmpty()) {
                            append(". ").append(result.messages.first())
                        }
                    },
                )
            } catch (exception: Exception) {
                emit("Error importando: ${exception.message}")
            } finally {
                local.value = local.value.copy(importing = false, importStatus = null)
            }
        }
    }

    fun importFolder(treeUri: Uri) {
        if (local.value.importing) return
        viewModelScope.launch {
            local.value = local.value.copy(importing = true, importStatus = "Escaneando carpeta…")
            try {
                val folderName = folderNameFromUri(treeUri)
                container.settingsRepository.updateServer {
                    it.copy(
                        lastScanTreeUri = treeUri.toString(),
                        lastScanFolderName = folderName,
                    )
                }
                val server = container.stateStore.snapshot().server
                val result = withContext(Dispatchers.IO) {
                    container.packageImporter.importTree(
                        treeUri = treeUri,
                        scanSubdirectories = server.scanSubdirectories,
                        readMetadata = server.readMetadata,
                        onProgress = { done, total ->
                            local.value = local.value.copy(
                                importStatus = "Importando $done de $total…",
                            )
                        },
                    )
                }
                emit(
                    buildString {
                        append("${result.added} PKG añadidos desde $folderName")
                        if (result.skipped > 0) append(", ${result.skipped} omitidos")
                        if (result.failed > 0) append(", ${result.failed} con error")
                    },
                )
            } catch (exception: Exception) {
                emit("Error escaneando la carpeta: ${exception.message}")
            } finally {
                local.value = local.value.copy(importing = false, importStatus = null)
            }
        }
    }

    fun rescanFolder() {
        if (local.value.importing) return
        viewModelScope.launch {
            val server = container.stateStore.snapshot().server
            val hasAllFilesAccess = StorageAccess.hasAllFilesAccess(container.appContext)
            val path = server.scanPath
            val uriString = server.lastScanTreeUri

            if (!hasAllFilesAccess && uriString == null) {
                emit("Concede acceso a todos los archivos o importa una carpeta primero")
                return@launch
            }

            val label = if (hasAllFilesAccess && path.isNotBlank()) {
                File(path).name.ifBlank { path }
            } else {
                server.lastScanFolderName ?: "carpeta"
            }
            local.value = local.value.copy(
                importing = true,
                importStatus = "Sincronizando $label…",
            )
            try {
                val result = withContext(Dispatchers.IO) {
                    if (hasAllFilesAccess && path.isNotBlank()) {
                        container.packageImporter.importFromPath(
                            rootPath = path,
                            scanSubdirectories = server.scanSubdirectories,
                            readMetadata = server.readMetadata,
                            onProgress = { done, total ->
                                local.value = local.value.copy(
                                    importStatus = "Sincronizando $done de $total…",
                                )
                            },
                        )
                    } else {
                        container.packageImporter.importTree(
                            treeUri = Uri.parse(uriString!!),
                            scanSubdirectories = server.scanSubdirectories,
                            readMetadata = server.readMetadata,
                            onProgress = { done, total ->
                                local.value = local.value.copy(
                                    importStatus = "Sincronizando $done de $total…",
                                )
                            },
                        )
                    }
                }
                emit(
                    buildString {
                        append("Sincronización: ${result.added} nuevos")
                        if (result.skipped > 0) append(", ${result.skipped} ya estaban")
                        if (result.failed > 0) append(", ${result.failed} con error")
                    },
                )
            } catch (exception: Exception) {
                emit("No se pudo sincronizar la carpeta: ${exception.message}")
            } finally {
                local.value = local.value.copy(importing = false, importStatus = null)
            }
        }
    }

    fun addToQueue(pkgId: String) {
        viewModelScope.launch {
            val pkg = container.packageRepository.find(pkgId) ?: return@launch
            val result = container.queueRepository.add(pkg)
            emit(
                if (result.added) {
                    "${pkg.effectiveTitle} añadido a la cola"
                } else {
                    result.reason ?: "No se pudo añadir a la cola"
                },
            )
        }
    }

    fun addGroupToQueue(group: PackageGroup) {
        viewModelScope.launch {
            var added = 0
            var skipped = 0
            group.packages.forEach { pkg ->
                val result = container.queueRepository.add(pkg)
                if (result.added) added++ else skipped++
            }
            emit(
                buildString {
                    append("Lote ${group.titleId ?: group.title}: $added añadido(s)")
                    if (skipped > 0) append(", $skipped ya estaban")
                },
            )
        }
    }

    fun removePackage(pkgId: String) {
        viewModelScope.launch {
            val state = container.stateStore.snapshot()
            val pkg = state.packages.firstOrNull { it.id == pkgId } ?: return@launch
            val active = state.queue.any {
                it.pkgId == pkgId &&
                    (it.status == QueueStatus.INSTALLING || it.status == QueueStatus.PAUSED)
            }
            if (active) {
                emit("No se puede eliminar un PKG con una instalación activa")
                return@launch
            }

            val deletion = withContext(Dispatchers.IO) {
                container.packageRepository.deleteFile(pkg)
            }
            if (deletion.isFailure) {
                emit(
                    "No se pudo borrar el archivo del almacenamiento: " +
                        (deletion.exceptionOrNull()?.message ?: "error desconocido"),
                )
                return@launch
            }

            container.queueRepository.removeByPkg(pkgId)
            container.packageRepository.remove(pkgId)
            emit("${pkg.effectiveTitle} eliminado de la app y del almacenamiento")
        }
    }

    fun refreshMetadata(pkgId: String) {
        viewModelScope.launch {
            val pkg = container.packageRepository.find(pkgId) ?: return@launch
            val result = withContext(Dispatchers.IO) {
                container.packageImporter.refreshMetadata(pkg)
            }
            emit(
                if (result != null) {
                    "Metadatos actualizados para ${pkg.effectiveTitle}"
                } else {
                    "No se pudieron leer los metadatos de este PKG"
                },
            )
        }
    }

    fun toggleGrouping() {
        viewModelScope.launch {
            container.settingsRepository.updateSettings {
                it.copy(groupPackagesByTitleId = !it.groupPackagesByTitleId)
            }
        }
    }

    fun toggleGroup(key: String) {
        viewModelScope.launch {
            container.settingsRepository.updateSettings { settings ->
                val collapsed = settings.collapsedCusaGroups
                settings.copy(
                    collapsedCusaGroups = if (key in collapsed) {
                        collapsed - key
                    } else {
                        collapsed + key
                    },
                )
            }
        }
    }

    fun toggleAllGroups() {
        viewModelScope.launch {
            val groups = uiState.value.groups
            if (groups.isEmpty()) return@launch
            val collapsed = container.stateStore.snapshot().settings.collapsedCusaGroups
            val allCollapsed = groups.all { it.key in collapsed }
            container.settingsRepository.updateSettings {
                it.copy(
                    collapsedCusaGroups = if (allCollapsed) {
                        emptySet()
                    } else {
                        groups.map { group -> group.key }.toSet()
                    },
                )
            }
        }
    }

    fun packageUrl(pkg: PkgFile): String? = container.serverManager.packageUrl(pkg)

    private fun groupPackages(packages: List<PkgFile>): List<PackageGroup> {
        return packages
            .groupBy { pkg ->
                pkg.titleId
                    ?: PkgNaming.titleIdFromContentId(pkg.contentId)
                    ?: pkg.displayName.lowercase()
            }
            .map { (key, groupPackages) ->
                val sorted = groupPackages.sortedWith(
                    compareBy<PkgFile>(
                        {
                            PkgNaming.packageSortRank(
                                PkgNaming.effectiveCategory(it.category, it.displayName),
                            )
                        },
                        { it.appVersion ?: "" },
                        { it.displayName.lowercase() },
                    ),
                )
                val title = sorted.firstOrNull {
                    PkgNaming.isBaseCategory(
                        PkgNaming.effectiveCategory(it.category, it.displayName),
                    )
                }?.effectiveTitle ?: sorted.first().effectiveTitle
                PackageGroup(
                    key = key,
                    title = title,
                    titleId = sorted.firstNotNullOfOrNull { it.titleId },
                    packages = sorted,
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    private fun folderNameFromUri(uri: Uri): String {
        val raw = uri.lastPathSegment ?: return "carpeta"
        return raw.substringAfterLast(':').substringAfterLast('/').ifBlank { "carpeta" }
            .replace("%20", " ")
    }

    private fun emit(message: String) {
        _events.tryEmit(message)
    }
}
