package com.remotesend.app.ui.screens.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remotesend.app.domain.model.QueueItem
import com.remotesend.app.domain.model.QueueStatus
import com.remotesend.app.domain.model.ServerRuntime
import com.remotesend.app.domain.util.PkgNaming
import com.remotesend.app.ui.CollectUiMessages
import com.remotesend.app.ui.appViewModel
import com.remotesend.app.ui.components.ConfirmDialog
import com.remotesend.app.ui.components.EmptyState
import com.remotesend.app.ui.components.PackageKindPill
import com.remotesend.app.ui.components.PkgArtwork
import com.remotesend.app.ui.components.ScreenTopBar
import com.remotesend.app.ui.components.SectionCard
import com.remotesend.app.ui.components.SettingSwitchRow
import com.remotesend.app.ui.components.StatusPill
import com.remotesend.app.ui.categoryLabel
import com.remotesend.app.ui.consoleTypeLabel
import com.remotesend.app.ui.statusLabel

@Composable
fun QueueScreen(
    onOpenPackages: () -> Unit,
    onOpenConsole: () -> Unit,
) {
    val viewModel: QueueViewModel = appViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CollectUiMessages(viewModel.events)

    var menuExpanded by remember { mutableStateOf(false) }
    var removeCandidate by remember { mutableStateOf<QueueItem?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val queuedCount = state.queue.count { it.status == QueueStatus.QUEUED }
    val activeCount = state.queue.count {
        it.status == QueueStatus.INSTALLING || it.status == QueueStatus.PAUSED
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "Cola de instalación",
            subtitle = "$queuedCount en espera · $activeCount activa(s)",
            actions = {
                IconButton(onClick = viewModel::startNext) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Iniciar siguiente")
                }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Más opciones")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Quitar finalizados") },
                        leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            viewModel.clearFinished()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Reiniciar cola") },
                        leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            confirmReset = true
                        },
                    )
                }
            },
        )

        if (state.serverRuntime !is ServerRuntime.Running) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SectionCard(
                    title = "Servidor detenido",
                    subtitle = "La consola necesita descargar los PKG desde este teléfono",
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = onOpenConsole) {
                            Icon(
                                Icons.Outlined.Wifi,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("Ir a Consola", modifier = Modifier.padding(start = 6.dp))
                        }
                        Text(
                            text = "Inicia el servidor antes de instalar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (state.queue.isEmpty()) {
            EmptyState(
                title = "La cola está vacía",
                message = "Añade PKG desde la biblioteca para preparar la instalación.",
                action = {
                    Button(onClick = onOpenPackages) {
                        Text("Abrir paquetes")
                    }
                },
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionCard(title = "Automatización") {
                    SettingSwitchRow(
                        title = "Auto-avanzar",
                        subtitle = "Inicia el siguiente PKG cuando termina el actual",
                        checked = state.autoAdvance,
                        onCheckedChange = { viewModel.toggleAutoAdvance() },
                    )
                }
            }
            itemsIndexed(state.queue, key = { _, item -> item.id }) { index, item ->
                QueueRow(
                    item = item,
                    position = index + 1,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.queue.lastIndex,
                    isServerRunning = state.serverRuntime is ServerRuntime.Running,
                    onMoveUp = { viewModel.move(item.id, -1) },
                    onMoveDown = { viewModel.move(item.id, 1) },
                    onStart = { viewModel.startItem(item.id) },
                    onResume = { viewModel.resumeItem(item.id) },
                    onPause = viewModel::pauseActive,
                    onStop = viewModel::stopActive,
                    onRemove = { removeCandidate = item },
                )
            }
        }
    }

    val candidate = removeCandidate
    if (candidate != null) {
        val isActive = candidate.status == QueueStatus.INSTALLING ||
            candidate.status == QueueStatus.PAUSED
        ConfirmDialog(
            title = "Quitar de la cola",
            text = if (isActive) {
                "Se detendrá la instalación de ${candidate.effectiveTitle} y se quitará " +
                    "de la cola. El PKG seguirá en la biblioteca."
            } else {
                "Se quitará ${candidate.effectiveTitle} de la cola. " +
                    "El PKG seguirá en la biblioteca."
            },
            confirmLabel = "Quitar",
            icon = Icons.Outlined.Delete,
            onConfirm = { viewModel.removeItem(candidate.id) },
            onDismiss = { removeCandidate = null },
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reiniciar cola",
            text = "Se detendrá la instalación activa y se vaciará toda la cola.",
            confirmLabel = "Reiniciar",
            onConfirm = viewModel::resetQueue,
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isServerRunning: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onRemove: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    PkgArtwork(iconPath = item.iconPath, modifier = Modifier.size(58.dp))
                    androidx.compose.material3.Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 3.dp,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        Text(
                            text = position.toString(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.effectiveTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PackageKindPill(
                            PkgNaming.effectiveCategory(item.category, item.displayName),
                        )
                        Text(
                            text = listOfNotNull(
                                item.titleId,
                                item.appVersion?.let { "v$it" },
                                PkgNaming.formatBytes(item.sizeBytes),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item.consoleType?.let {
                        Text(
                            text = consoleTypeLabel(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StatusPill(item.status)
            }

            if (item.status != QueueStatus.QUEUED || item.progress > 0) {
                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                val eta = when {
                    item.restSeconds > 0 -> item.restSeconds
                    item.speedBytesPerSecond > 0 && item.totalBytes > item.transferredBytes ->
                        (item.totalBytes - item.transferredBytes) / item.speedBytesPerSecond
                    else -> 0L
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = buildString {
                            append("${item.progress}%")
                            PkgNaming.formatSpeed(item.speedBytesPerSecond)?.let {
                                append(" · ").append(it)
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    PkgNaming.formatDuration(eta)?.let {
                        Text(text = "Quedan $it", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (item.totalBytes > 0) {
                    Text(
                        text = "${PkgNaming.formatBytes(item.transferredBytes)} de " +
                            PkgNaming.formatBytes(item.totalBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item.serverUrl?.let { url ->
                Text(
                    text = url,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item.taskId != null) {
                Text(
                    text = "Task ID: ${item.taskId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == QueueStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Subir")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "Bajar")
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))

                when (item.status) {
                    QueueStatus.INSTALLING -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Outlined.Pause, contentDescription = "Pausar")
                        }
                        IconButton(onClick = onStop) {
                            Icon(Icons.Outlined.Stop, contentDescription = "Detener")
                        }
                    }

                    QueueStatus.PAUSED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "Reanudar")
                        }
                        IconButton(onClick = onStop) {
                            Icon(Icons.Outlined.Stop, contentDescription = "Detener")
                        }
                    }

                    QueueStatus.STOPPED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "Reanudar")
                        }
                    }

                    else -> {
                        FilledTonalButton(
                            onClick = onStart,
                            enabled = isServerRunning,
                        ) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("Iniciar", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }

                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Quitar de la cola")
                }
            }
        }
    }
}
