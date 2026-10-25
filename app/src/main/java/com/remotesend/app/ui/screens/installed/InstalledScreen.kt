package com.remotesend.app.ui.screens.installed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remotesend.app.domain.model.InstalledTitle
import com.remotesend.app.domain.util.PkgNaming
import com.remotesend.app.ui.CollectUiMessages
import com.remotesend.app.ui.appViewModel
import com.remotesend.app.ui.components.ConfirmDialog
import com.remotesend.app.ui.components.DividerLine
import com.remotesend.app.ui.components.EmptyState
import com.remotesend.app.ui.components.PackageKindPill
import com.remotesend.app.ui.components.PkgArtwork
import com.remotesend.app.ui.components.ScreenTopBar
import com.remotesend.app.ui.components.SectionCard
import com.remotesend.app.ui.formatTimestamp

@Composable
fun InstalledScreen(
    onBack: () -> Unit,
) {
    val viewModel: InstalledViewModel = appViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CollectUiMessages(viewModel.events)
    var pendingUninstall by remember { mutableStateOf<UninstallTarget?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "Juegos instalados",
            subtitle = "PS4 · FTP GoldHEN + PS4RPI · Fase 1",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = viewModel::refresh,
                    enabled = !state.loading,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar")
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.loading && state.titles.isEmpty()) {
                item {
                    SectionCard(title = "Consultando la consola") {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Leyendo app.db e iconos de la PS4 por FTP…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.error?.let { error ->
                item {
                    SectionCard(title = "No se pudo cargar") {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = viewModel::refresh) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Text("Reintentar", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }

            state.warning?.let { warning ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            if (state.titles.isNotEmpty()) {
                item {
                    InstalledSummary(
                        titles = state.titles,
                        lastCheckedAt = state.lastCheckedAt,
                    )
                }
            }

            if (!state.loading && state.error == null && state.titles.isEmpty()) {
                item {
                    val ftpFailed = state.warning != null
                    EmptyState(
                        title = if (ftpFailed) {
                            "No se pudo leer la lista"
                        } else {
                            "No hay juegos instalados"
                        },
                        message = if (ftpFailed) {
                            "Abre GoldHEN con FTP en el puerto 2121 y PS4RPI, " +
                                "revisa la misma red y vuelve a comprobar."
                        } else {
                            "La PS4 no reportó juegos instalados en app.db."
                        },
                        icon = if (ftpFailed) Icons.Outlined.CloudOff else Icons.Outlined.Storage,
                        action = {
                            Button(onClick = viewModel::refresh) {
                                Text("Volver a comprobar")
                            }
                        },
                    )
                }
            }

            if (state.actionInProgress) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            items(state.titles, key = { it.titleId }) { title ->
                InstalledTitleCard(
                    title = title,
                    actionInProgress = state.actionInProgress,
                    onUninstall = { pendingUninstall = it },
                )
            }

            if (state.titles.isNotEmpty()) {
                item {
                    Text(
                        text = "Fase 1: lista real leída de app.db por el FTP de GoldHEN " +
                            "(puerto 2121); tamaños reportados por la PS4. La desinstalación se " +
                            "envía a PS4RPI. Las actualizaciones/DLC solo aparecen si están en tu " +
                            "biblioteca; el espacio libre total requiere un agente o app.db " +
                            "ampliado en una fase posterior.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    val target = pendingUninstall
    if (target != null) {
        ConfirmDialog(
            title = "Desinstalar ${target.label}",
            text = "Se desinstalará ${target.description} de la consola. " +
                "El PKG seguirá guardado en el teléfono.",
            confirmLabel = "Desinstalar",
            icon = Icons.Outlined.Delete,
            onConfirm = {
                when (target) {
                    is UninstallTarget.Game -> viewModel.uninstallGame(target.titleId)
                    is UninstallTarget.Update -> viewModel.uninstallUpdate(target.titleId)
                    is UninstallTarget.Dlc -> viewModel.uninstallDlc(target.contentId)
                }
            },
            onDismiss = { pendingUninstall = null },
        )
    }
}

@Composable
private fun InstalledSummary(
    titles: List<InstalledTitle>,
    lastCheckedAt: Long?,
) {
    val total = titles.sumOf { it.installedSizeBytes }
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        Color(0xFF2E7D32),
        Color(0xFFB26A00),
        Color(0xFF7B1FA2),
    )
    val sorted = titles
        .filter { it.installedSizeBytes > 0L }
        .sortedByDescending { it.installedSizeBytes }
    val top = sorted.take(5)
    val otherSize = sorted.drop(5).sumOf { it.installedSizeBytes }
    val slices = buildList {
        top.forEachIndexed { index, title ->
            add(DonutSlice(title.title, title.installedSizeBytes, colors[index % colors.size]))
        }
        if (otherSize > 0L) {
            add(DonutSlice("Otros", otherSize, colors.last()))
        }
    }

    SectionCard(
        title = "Distribución instalada",
        subtitle = "Tamaño real de app.db",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InstalledDonutChart(
                slices = slices,
                total = total,
                modifier = Modifier.size(142.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${titles.size} juego(s)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = PkgNaming.formatBytes(total),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Tamaño reportado por la consola. El espacio libre total no está " +
                        "disponible con PS4RPI.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lastCheckedAt != null) {
                    Text(
                        text = "Actualizado ${formatTimestamp(lastCheckedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (slices.isNotEmpty() && total > 0L) {
            DividerLine()
            slices.forEach { slice ->
                DonutLegendRow(slice, total)
            }
        }
    }
}

@Composable
private fun InstalledDonutChart(
    slices: List<DonutSlice>,
    total: Long,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 24.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color.Gray.copy(alpha = 0.16f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke),
            )
            if (total > 0L && slices.isNotEmpty()) {
                var start = -90f
                slices.forEach { slice ->
                    val sweep = (slice.size.toDouble() / total * 360.0).toFloat()
                    drawArc(
                        color = slice.color,
                        startAngle = start,
                        sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Butt),
                    )
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = PkgNaming.formatBytes(total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "instalado",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DonutLegendRow(slice: DonutSlice, total: Long) {
    val percent = if (total > 0L) {
        (slice.size.toDouble() / total * 100.0).toInt()
    } else {
        0
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(slice.color, CircleShape),
        )
        Text(
            text = slice.label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${PkgNaming.formatBytes(slice.size)} · $percent%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstalledTitleCard(
    title: InstalledTitle,
    actionInProgress: Boolean,
    onUninstall: (UninstallTarget) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val displayPackage = title.basePackage ?: title.packages.firstOrNull()
    val categories = title.packages
        .map { PkgNaming.effectiveCategory(it.category, it.displayName) }
        .distinct()
        .ifEmpty { listOf("gd") }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PkgArtwork(
                iconPath = title.iconPath ?: displayPackage?.iconPath,
                modifier = Modifier.size(64.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${title.titleId} · ${PkgNaming.formatBytes(title.installedSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    categories.forEach { category ->
                        PackageKindPill(category)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                InstalledBadge()
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = !actionInProgress,
                ) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Gestionar")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Desinstalar juego") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        enabled = !actionInProgress,
                        onClick = {
                            menuExpanded = false
                            onUninstall(
                                UninstallTarget.Game(title.titleId, title.title),
                            )
                        },
                    )
                    if (title.updates.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Desinstalar actualización") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            enabled = !actionInProgress,
                            onClick = {
                                menuExpanded = false
                                onUninstall(
                                    UninstallTarget.Update(title.titleId, title.title),
                                )
                            },
                        )
                    }
                    title.dlcs.forEach { dlc ->
                        val contentId = dlc.contentId
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Desinstalar DLC: ${dlc.effectiveTitle}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Delete, contentDescription = null)
                            },
                            enabled = !actionInProgress && !contentId.isNullOrBlank(),
                            onClick = {
                                menuExpanded = false
                                if (!contentId.isNullOrBlank()) {
                                    onUninstall(UninstallTarget.Dlc(contentId, dlc.effectiveTitle))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
            )
            Text(text = "Instalado", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private data class DonutSlice(
    val label: String,
    val size: Long,
    val color: Color,
)

private sealed interface UninstallTarget {
    val label: String
    val description: String
    val titleId: String

    data class Game(
        override val titleId: String,
        val title: String,
    ) : UninstallTarget {
        override val label = "juego"
        override val description = title
    }

    data class Update(
        override val titleId: String,
        val title: String,
    ) : UninstallTarget {
        override val label = "actualización"
        override val description = "la actualización de $title"
    }

    data class Dlc(
        val contentId: String,
        val title: String,
    ) : UninstallTarget {
        override val titleId = ""
        override val label = "DLC"
        override val description = title
    }
}
