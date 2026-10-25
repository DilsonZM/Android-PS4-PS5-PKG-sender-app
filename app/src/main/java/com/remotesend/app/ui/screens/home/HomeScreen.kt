package com.remotesend.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remotesend.app.core.AppLinks
import com.remotesend.app.domain.model.QueueStatus
import com.remotesend.app.domain.model.ServerRuntime
import com.remotesend.app.domain.util.PkgNaming
import com.remotesend.app.ui.CollectUiMessages
import com.remotesend.app.ui.appViewModel
import com.remotesend.app.ui.components.ConsoleStatusAction
import com.remotesend.app.ui.components.CopyButton
import com.remotesend.app.ui.components.InfoRow
import com.remotesend.app.ui.components.PkgArtwork
import com.remotesend.app.ui.components.ScreenTopBar
import com.remotesend.app.ui.components.SectionCard
import com.remotesend.app.ui.components.ServerStatusRow
import com.remotesend.app.domain.model.ConsoleConnectionStatus
import com.remotesend.app.ui.consoleTypeLabel
import com.remotesend.app.ui.statusLabel

@Composable
fun HomeScreen(
    onOpenPackages: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val viewModel: HomeViewModel = appViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CollectUiMessages(viewModel.events)
    val uriHandler = LocalUriHandler.current

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "RemoteSend",
            subtitle = "Enviar PKG a PS4 y PS5",
            actions = {
                IconButton(
                    onClick = {
                        runCatching { uriHandler.openUri(AppLinks.PAYPAL_SUPPORT_URL) }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalCafe,
                        contentDescription = "Invítame un café con PayPal",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                ConsoleStatusAction(
                    connection = state.connection,
                    onClick = viewModel::checkConsole,
                )
                IconButton(onClick = onOpenLogs) {
                    Icon(Icons.Outlined.BugReport, contentDescription = "Registros")
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ServerCard(
                    state = state,
                    onStart = viewModel::startServer,
                    onStop = viewModel::stopServer,
                )
            }
            item {
                StatsRow(
                    packages = state.packagesCount,
                    queued = state.queuedCount,
                    completed = state.completedCount,
                    onOpenPackages = onOpenPackages,
                    onOpenQueue = onOpenQueue,
                )
            }
            val active = state.activeItem
            if (active != null) {
                item {
                    ActiveInstallCard(
                        title = active.effectiveTitle,
                        status = active.status,
                        progress = active.progress,
                        restSeconds = active.restSeconds,
                        transferredBytes = active.transferredBytes,
                        totalBytes = active.totalBytes,
                        speedBytesPerSecond = active.speedBytesPerSecond,
                        iconPath = active.iconPath,
                        message = active.message,
                        onPause = viewModel::pauseActive,
                        onResume = viewModel::resumeActive,
                        onStop = viewModel::stopActive,
                    )
                }
            }
            item {
                SectionCard(
                    title = "Consola",
                    subtitle = "Conexión con PS4/PS5",
                ) {
                    InfoRow("Modo", consoleTypeLabel(state.consoleConfig.type))
                    InfoRow(
                        "Dirección",
                        state.consoleConfig.host.ifBlank { "Sin configurar" },
                    )
                    val connection = state.connection
                    if (connection.status != ConsoleConnectionStatus.UNKNOWN &&
                        connection.message != null
                    ) {
                        Text(
                            text = connection.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connection.status == ConsoleConnectionStatus.CONNECTED) {
                                MaterialTheme.colorScheme.primary
                            } else if (connection.status == ConsoleConnectionStatus.DISCONNECTED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::checkConsole, enabled = !state.checking) {
                            if (state.checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.Wifi,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                text = if (state.checking) "Comprobando…" else "Comprobar",
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        OutlinedButton(onClick = onOpenConsole) {
                            Text("Configurar")
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Accesos rápidos") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOpenPackages,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Outlined.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("Paquetes", modifier = Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(
                            onClick = onOpenQueue,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.List,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("Cola", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Antes de empezar") {
                    Text(
                        text = "• Mantén el teléfono y la consola en la misma red Wi-Fi.\n" +
                            "• En PS4 inicia flatZ Remote Package Installer o GoldHEN.\n" +
                            "• En PS5 activa etaHEN con el RPI en el puerto 9090.\n" +
                            "• Desactiva el aislamiento de clientes (AP isolation) en el router.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    state: HomeUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    SectionCard(
        title = "Servidor local",
        subtitle = "La consola descarga los PKG desde este teléfono",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerStatusRow(state.serverState)
            when (val server = state.serverState) {
                is ServerRuntime.Running -> CopyButton(server.baseUrl, "URL del servidor")
                else -> Unit
            }
        }

        when (val server = state.serverState) {
            is ServerRuntime.Running -> {
                Text(
                    text = server.baseUrl,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "Heartbeat: ${server.heartbeatUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }

            ServerRuntime.Starting -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Preparando el servidor HTTP…")
            }

            is ServerRuntime.Error -> {
                Text(
                    text = server.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            ServerRuntime.Stopped -> {
                Text(
                    text = "Inicia el servidor para publicar tu biblioteca de PKG en la red local.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val running = state.serverState is ServerRuntime.Running
        if (running) {
            OutlinedButton(onClick = onStop) {
                Icon(
                    Icons.Outlined.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text("Detener servidor", modifier = Modifier.padding(start = 6.dp))
            }
        } else {
            Button(onClick = onStart) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text("Iniciar servidor", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun StatsRow(
    packages: Int,
    queued: Int,
    completed: Int,
    onOpenPackages: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            value = packages.toString(),
            label = "PKG",
            icon = { Icon(Icons.Outlined.Inventory2, contentDescription = null) },
            onClick = onOpenPackages,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = queued.toString(),
            label = "En cola",
            icon = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null) },
            onClick = onOpenQueue,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = completed.toString(),
            label = "Listos",
            icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
            onClick = onOpenQueue,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon()
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActiveInstallCard(
    title: String,
    status: QueueStatus,
    progress: Int,
    restSeconds: Long,
    transferredBytes: Long,
    totalBytes: Long,
    speedBytesPerSecond: Long,
    iconPath: String?,
    message: String?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val eta = when {
        restSeconds > 0 -> restSeconds
        speedBytesPerSecond > 0 && totalBytes > transferredBytes ->
            (totalBytes - transferredBytes) / speedBytesPerSecond
        else -> 0L
    }
    SectionCard(title = "Instalación en curso") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PkgArtwork(iconPath = iconPath, modifier = Modifier.size(54.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = statusLabel(status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = buildString {
                if (totalBytes > 0) {
                    append(PkgNaming.formatBytes(transferredBytes))
                    append(" de ")
                    append(PkgNaming.formatBytes(totalBytes))
                    append(" · ")
                }
                append(PkgNaming.formatSpeed(speedBytesPerSecond) ?: "Calculando velocidad")
                PkgNaming.formatDuration(eta)?.let { append(" · quedan ").append(it) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { progress.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("$progress%", style = MaterialTheme.typography.labelLarge)
            Text(statusLabel(status), style = MaterialTheme.typography.labelLarge)
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status == QueueStatus.PAUSED || status == QueueStatus.STOPPED) {
                Button(onClick = onResume) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Reanudar", modifier = Modifier.padding(start = 6.dp))
                }
            } else {
                OutlinedButton(onClick = onPause) {
                    Icon(
                        Icons.Outlined.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Pausar", modifier = Modifier.padding(start = 6.dp))
                }
            }
            TextButton(onClick = onStop) {
                Text("Detener")
            }
        }
    }
}
