package com.remotesend.app.ui.screens.packages

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remotesend.app.domain.model.PackageGroup
import com.remotesend.app.domain.model.PkgFile
import com.remotesend.app.domain.model.QueueItem
import com.remotesend.app.domain.model.isTerminal
import com.remotesend.app.domain.util.PkgNaming
import com.remotesend.app.ui.CollectUiMessages
import com.remotesend.app.ui.LocalSnackbarHostState
import com.remotesend.app.ui.OpenDocumentTreeWithWrite
import com.remotesend.app.ui.OpenMultipleDocumentsWithWrite
import com.remotesend.app.ui.appViewModel
import com.remotesend.app.ui.categoryLabel
import com.remotesend.app.ui.components.ConfirmDialog
import com.remotesend.app.ui.components.EmptyState
import com.remotesend.app.ui.components.InfoRow
import com.remotesend.app.ui.components.PackageKindPill
import com.remotesend.app.ui.components.PkgArtwork
import com.remotesend.app.ui.components.ScreenTopBar
import com.remotesend.app.ui.components.SectionCard
import com.remotesend.app.ui.components.StatusPill
import kotlinx.coroutines.launch

@Composable
fun PackagesScreen() {
    val viewModel: PackagesViewModel = appViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CollectUiMessages(viewModel.events)
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        OpenMultipleDocumentsWithWrite(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }
    val folderPicker = rememberLauncherForActivityResult(
        OpenDocumentTreeWithWrite(),
    ) { uri ->
        if (uri != null) viewModel.importFolder(uri)
    }

    var selectedPackage by remember { mutableStateOf<PkgFile?>(null) }
    var deleteCandidate by remember { mutableStateOf<PkgFile?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "Paquetes",
            subtitle = "${state.allPackagesCount} archivo(s) en la biblioteca",
            actions = {
                IconButton(onClick = viewModel::toggleGrouping) {
                    Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription = if (state.groupByTitleId) {
                            "Desagrupar paquetes"
                        } else {
                            "Agrupar por CUSA"
                        },
                        tint = if (state.groupByTitleId) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(
                    onClick = viewModel::rescanFolder,
                    enabled = !state.importing,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Re-escanear carpeta")
                }
                IconButton(onClick = { folderPicker.launch(null) }) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Añadir carpeta")
                }
                IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Añadir PKG")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    BasicTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (state.query.isEmpty()) {
                                Text(
                                    text = "Buscar por título, CUSA o Content ID",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        },
                    )
                    if (state.query.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setQuery("") },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Limpiar búsqueda",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            val allCollapsed = state.groups.isNotEmpty() &&
                state.groups.all { it.key in state.collapsedGroups }
            val folderName = state.scanFolderName
            if (folderName != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.CreateNewFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Carpeta vinculada: $folderName",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = if (state.autoScanOnStartup) {
                                    "Auto-escaneo activo al iniciar"
                                } else {
                                    "Auto-escaneo desactivado"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.groupByTitleId && state.groups.isNotEmpty()) {
                            IconButton(onClick = viewModel::toggleAllGroups) {
                                Icon(
                                    imageVector = if (allCollapsed) {
                                        Icons.Outlined.ExpandMore
                                    } else {
                                        Icons.Outlined.ExpandLess
                                    },
                                    contentDescription = if (allCollapsed) {
                                        "Expandir todos los CUSA"
                                    } else {
                                        "Contraer todos los CUSA"
                                    },
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            if (state.importing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = state.importStatus ?: "Importando…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        if (state.packages.isEmpty()) {
            EmptyState(
                title = if (state.query.isBlank()) "Sin paquetes" else "Sin resultados",
                message = if (state.query.isBlank()) {
                    "Añade archivos .pkg desde el almacenamiento del teléfono o escanea una carpeta."
                } else {
                    "Prueba con otro título, Title ID o Content ID."
                },
                icon = Icons.Outlined.Inventory2,
                action = if (state.query.isBlank()) {
                    {
                        Button(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Text("Añadir PKG", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                } else {
                    null
                },
            )
            return@Column
        }

        val renderPackage: @Composable (PkgFile) -> Unit = { pkg ->
            val queueItem = state.queue.firstOrNull {
                it.pkgId == pkg.id && !it.status.isTerminal
            }
            PackageRow(
                pkg = pkg,
                queueItem = queueItem,
                serverRunning = state.serverRuntime is com.remotesend.app.domain.model.ServerRuntime.Running,
                onAdd = { viewModel.addToQueue(pkg.id) },
                onDetails = { selectedPackage = pkg },
                onRefresh = { viewModel.refreshMetadata(pkg.id) },
                onCopyUrl = {
                    val url = viewModel.packageUrl(pkg)
                    if (url != null) {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("PKG URL", url))
                        scope.launch { snackbar.showSnackbar("URL copiada") }
                    }
                },
                onDelete = { deleteCandidate = pkg },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.groupByTitleId && state.groups.isNotEmpty()) {
                state.groups.forEach { group ->
                    item(key = "group-${group.key}") {
                        PackageGroupHeader(
                            group = group,
                            collapsed = group.key in state.collapsedGroups,
                            onToggle = { viewModel.toggleGroup(group.key) },
                            onAddGroup = { viewModel.addGroupToQueue(group) },
                        )
                    }
                    if (group.key !in state.collapsedGroups) {
                        items(group.packages, key = { it.id }) { pkg ->
                            renderPackage(pkg)
                        }
                    }
                }
            } else {
                items(state.packages, key = { it.id }) { pkg ->
                    renderPackage(pkg)
                }
            }
        }
    }

    val details = selectedPackage
    if (details != null) {
        val url = viewModel.packageUrl(details)
        PackageDetailsDialog(
            pkg = details,
            url = url,
            onDismiss = { selectedPackage = null },
        )
    }

    val toDelete = deleteCandidate
    if (toDelete != null) {
        ConfirmDialog(
            title = "Eliminar PKG del almacenamiento",
            text = "Se quitará ${toDelete.displayName} de la biblioteca y de la cola, " +
                "y se borrará el archivo del almacenamiento. Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar",
            icon = Icons.Outlined.Delete,
            onConfirm = { viewModel.removePackage(toDelete.id) },
            onDismiss = { deleteCandidate = null },
        )
    }
}

@Composable
private fun PackageRow(
    pkg: PkgFile,
    queueItem: QueueItem?,
    serverRunning: Boolean,
    onAdd: () -> Unit,
    onDetails: () -> Unit,
    onRefresh: () -> Unit,
    onCopyUrl: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PkgArtwork(iconPath = pkg.iconPath, modifier = Modifier.size(58.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = pkg.effectiveTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pkg.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PackageKindPill(
                        PkgNaming.effectiveCategory(pkg.category, pkg.displayName),
                    )
                    Text(
                        text = listOfNotNull(
                            pkg.titleId,
                            pkg.appVersion?.let { "v$it" },
                            PkgNaming.formatBytes(pkg.sizeBytes),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (queueItem != null) {
                    StatusPill(queueItem.status)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = onAdd,
                    enabled = queueItem == null,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Añadir a la cola")
                }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Más opciones")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Ver detalles") },
                        leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDetails()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Actualizar metadatos") },
                        leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRefresh()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copiar URL del PKG") },
                        leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                        enabled = serverRunning,
                        onClick = {
                            menuExpanded = false
                            onCopyUrl()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

private data class GroupKindUi(
    val category: String?,
    val label: String,
)

private fun groupKinds(group: PackageGroup): List<GroupKindUi> {
    val categories = group.packages
        .map { PkgNaming.effectiveCategory(it.category, it.displayName) }
        .distinct()
    val hasBase = categories.any { PkgNaming.isBaseCategory(it) }
    val hasUpdate = categories.any { PkgNaming.isUpdateCategory(it) }
    val hasDlc = categories.any { PkgNaming.isDlcCategory(it) }
    val hasUnknown = categories.any {
        it == null || (!PkgNaming.isBaseCategory(it) &&
            !PkgNaming.isUpdateCategory(it) &&
            !PkgNaming.isDlcCategory(it))
    }
    val compact = hasBase && hasUpdate && hasDlc
    return buildList {
        if (hasBase) {
            add(GroupKindUi("gd", if (compact) "Juego" else "Juego base"))
        }
        if (hasUpdate) {
            add(GroupKindUi("gp", if (compact) "Act." else "Actualización"))
        }
        if (hasDlc) {
            add(GroupKindUi("ac", "DLC"))
        }
        if (hasUnknown) {
            add(GroupKindUi(null, "Paquete"))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackageGroupHeader(
    group: PackageGroup,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onAddGroup: () -> Unit,
) {
    OutlinedCard(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PkgArtwork(iconPath = group.cover.iconPath, modifier = Modifier.size(54.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        group.titleId,
                        "${group.packages.size} paquete(s)",
                        PkgNaming.formatBytes(group.totalBytes),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    groupKinds(group).forEach { kind ->
                        PackageKindPill(kind.category, kind.label)
                    }
                }
            }
            IconButton(
                onClick = onAddGroup,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlaylistAdd,
                    contentDescription = "Añadir todo el lote a la cola",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Icon(
                imageVector = if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                contentDescription = if (collapsed) "Expandir CUSA" else "Contraer CUSA",
            )
        }
    }
}

@Composable
private fun PackageDetailsDialog(
    pkg: PkgFile,
    url: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pkg.effectiveTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InfoRow("Archivo", pkg.displayName)
                InfoRow("Title ID", pkg.titleId ?: "No disponible")
                InfoRow("Content ID", pkg.contentId ?: "No disponible", monospace = true)
                InfoRow("Versión", pkg.appVersion ?: "No disponible")
                InfoRow("Categoría", categoryLabel(pkg.category) ?: pkg.category ?: "No disponible")
                InfoRow("Tamaño", PkgNaming.formatBytes(pkg.sizeBytes))
                InfoRow("Icono", if (pkg.iconPath != null) "Extraído" else "No disponible")
                InfoRow("Metadatos", if (pkg.metadataRead) "Leídos del PKG" else "Desde el nombre")
                if (url != null) {
                    InfoRow("URL PKG", url, monospace = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}
