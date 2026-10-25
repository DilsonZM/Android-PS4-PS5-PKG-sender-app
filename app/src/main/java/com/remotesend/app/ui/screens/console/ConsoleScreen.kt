package com.remotesend.app.ui.screens.console

import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remotesend.app.domain.model.ConsoleConnectionStatus
import com.remotesend.app.domain.model.ConsoleType
import com.remotesend.app.domain.model.ServerRuntime
import com.remotesend.app.core.StorageAccess
import com.remotesend.app.ui.CollectUiMessages
import com.remotesend.app.ui.appViewModel
import com.remotesend.app.ui.components.ConsoleStatusAction
import com.remotesend.app.ui.components.CopyButton
import com.remotesend.app.ui.components.InfoRow
import com.remotesend.app.ui.components.ScreenTopBar
import com.remotesend.app.ui.components.SectionCard
import com.remotesend.app.ui.components.ServerStatusRow
import com.remotesend.app.ui.components.SettingSwitchRow
import com.remotesend.app.ui.consoleTypeLabel

@Composable
fun ConsoleScreen() {
    val viewModel: ConsoleViewModel = appViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CollectUiMessages(viewModel.events)
    val form = state.form
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allFilesAccess by remember {
        mutableStateOf(StorageAccess.hasAllFilesAccess(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allFilesAccess = StorageAccess.hasAllFilesAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "Consola y red",
            subtitle = "Configuración de PS4/PS5 y servidor local",
            actions = {
                ConsoleStatusAction(
                    connection = state.connection,
                    onClick = viewModel::checkConnection,
                )
                IconButton(onClick = viewModel::save, enabled = !state.saving) {
                    Icon(Icons.Outlined.Save, contentDescription = "Guardar")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(
                title = "PlayStation",
                subtitle = "Selecciona el modo o deja la detección automática",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConsoleType.entries.forEach { type ->
                        FilterChip(
                            selected = form.type == type,
                            onClick = {
                                viewModel.updateForm { it.copy(type = type) }
                            },
                            label = { Text(consoleTypeLabel(type)) },
                        )
                    }
                }

                OutlinedTextField(
                    value = form.host,
                    onValueChange = { value ->
                        viewModel.updateForm { it.copy(host = value) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("IP de la consola") },
                    placeholder = { Text("192.168.1.100") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Wifi, contentDescription = null)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = form.httpPort,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(httpPort = value.filter(Char::isDigit)) }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Puerto RPI") },
                        placeholder = { Text("12800") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = form.tcpPort,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(tcpPort = value.filter(Char::isDigit)) }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Puerto TCP") },
                        placeholder = { Text("9090") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                Text(
                    text = "flatZ original: 12800 · PS4RPI de njzydark: 12801. " +
                        "En modo automático se prueban ambos puertos.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = form.timeoutMs,
                        onValueChange = { value ->
                            viewModel.updateForm { it.copy(timeoutMs = value.filter(Char::isDigit)) }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Timeout (ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = form.pollIntervalMs,
                        onValueChange = { value ->
                            viewModel.updateForm {
                                it.copy(pollIntervalMs = value.filter(Char::isDigit))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Progreso (ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                SettingSwitchRow(
                    title = "Asumir PS5 en el puerto 9090",
                    subtitle = "El puerto TCP es compartido por PS4 GoldHEN y PS5 etaHEN",
                    checked = form.preferPs5ForTcp,
                    onCheckedChange = { enabled ->
                        viewModel.updateForm { it.copy(preferPs5ForTcp = enabled) }
                    },
                )

                val connection = state.connection
                if (connection.status != ConsoleConnectionStatus.UNKNOWN &&
                    connection.message != null
                ) {
                    Surface(
                        color = when (connection.status) {
                            ConsoleConnectionStatus.CONNECTED ->
                                MaterialTheme.colorScheme.primaryContainer

                            ConsoleConnectionStatus.DISCONNECTED ->
                                MaterialTheme.colorScheme.errorContainer

                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = connection.message,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            connection.latencyMs?.let {
                                Text(
                                    text = "Latencia: $it ms · Tipo: ${consoleTypeLabel(connection.type)}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            if (connection.ambiguousTcp) {
                                Text(
                                    text = "Si la detección es incorrecta, selecciona el modo manualmente.",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::checkConnection,
                        enabled = !state.checking,
                    ) {
                        if (state.checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.NetworkCheck,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = if (state.checking) "Comprobando…" else "Comprobar",
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::scanNetwork,
                        enabled = !state.scanning,
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (state.scanning) "Escaneando…" else "Escanear red",
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }

                Text(
                    text = "Escanea ${state.scanTotal.coerceAtLeast(253)} hosts de la red local " +
                        "probando el puerto RPI ${form.httpPort} y el TCP ${form.tcpPort}. " +
                        "Toca un resultado para rellenar la IP y el puerto automáticamente.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.scanning || state.scanTotal > 0) {
                    LinearProgressIndicator(
                        progress = {
                            if (state.scanTotal > 0) {
                                state.scanScanned.toFloat() / state.scanTotal
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = if (state.scanTotal > 0) {
                            "Escaneados ${state.scanScanned} de ${state.scanTotal}"
                        } else {
                            "Buscando consolas en la red local…"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!state.scanning && state.scanTotal > 0 && state.candidates.isEmpty()) {
                    Text(
                        text = "No se encontraron consolas con esos puertos. Comprueba que el " +
                            "RPI/GoldHEN/etaHEN esté activo y que el router no aísle clientes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                state.candidates.forEach { candidate ->
                    OutlinedCard(
                        onClick = { viewModel.applyCandidate(candidate) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Outlined.Wifi, contentDescription = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidate.host,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    text = "${consoleTypeLabel(candidate.suggestedType)} · " +
                                        "puertos ${candidate.openPorts.joinToString()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Outlined.Check, contentDescription = "Aplicar")
                        }
                    }
                }
            }

            SectionCard(
                title = "Servidor local",
                subtitle = "Interfaz y puerto desde el que se sirven los PKG",
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Interfaz de red",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::refreshAddresses) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar interfaces")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = form.bindIp.isBlank(),
                        onClick = {
                            viewModel.updateForm { it.copy(bindIp = "") }
                        },
                        label = { Text("Automática") },
                    )
                    state.addresses.forEach { address ->
                        FilterChip(
                            selected = form.bindIp == address.ip,
                            onClick = {
                                viewModel.updateForm { it.copy(bindIp = address.ip) }
                            },
                            label = { Text("${address.interfaceName}: ${address.ip}") },
                        )
                    }
                }

                if (state.addresses.isEmpty()) {
                    Text(
                        text = "No se detectaron interfaces IPv4. Conéctate por Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text(
                    text = "Auto-escaneo de PKG",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (allFilesAccess) {
                            "Acceso a todos los archivos concedido"
                        } else {
                            "Sin acceso a todos los archivos"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (allFilesAccess) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (!allFilesAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        OutlinedButton(
                            onClick = {
                                StorageAccess.allFilesSettingsIntent(context)?.let {
                                    context.startActivity(it)
                                }
                            },
                        ) {
                            Text("Conceder acceso")
                        }
                    }
                }
                Text(
                    text = "El selector de Android no permite elegir la raíz Download. " +
                        "Con acceso total la app puede escanear esa ruta automáticamente. " +
                        "Sin este permiso puedes importar archivos o una subcarpeta con SAF.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = form.scanPath,
                    onValueChange = { value ->
                        viewModel.updateForm { it.copy(scanPath = value) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Ruta de auto-escaneo") },
                    placeholder = { Text("/storage/emulated/0/Download") },
                )

                OutlinedTextField(
                    value = form.serverPort,
                    onValueChange = { value ->
                        viewModel.updateForm { it.copy(serverPort = value.filter(Char::isDigit)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Puerto del servidor") },
                    placeholder = { Text("8337") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                val previewHost = form.bindIp.ifBlank {
                    state.addresses.firstOrNull()?.ip ?: "IP-del-telefono"
                }
                InfoRow(
                    label = "URL prevista",
                    value = "http://$previewHost:${form.serverPort.ifBlank { "8337" }}",
                    monospace = true,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ServerStatusRow(state.serverRuntime)
                    when (val server = state.serverRuntime) {
                        is ServerRuntime.Running -> CopyButton(server.baseUrl, "URL del servidor")
                        else -> Unit
                    }
                }

                val running = state.serverRuntime is ServerRuntime.Running
                if (running) {
                    OutlinedButton(onClick = viewModel::stopServer) {
                        Icon(
                            Icons.Outlined.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Detener servidor", modifier = Modifier.padding(start = 6.dp))
                    }
                } else {
                    Button(onClick = viewModel::startServer) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Iniciar servidor", modifier = Modifier.padding(start = 6.dp))
                    }
                }

                SettingSwitchRow(
                    title = "Iniciar servidor al abrir la app",
                    checked = form.autoStart,
                    onCheckedChange = { value ->
                        viewModel.updateForm { it.copy(autoStart = value) }
                    },
                )
                SettingSwitchRow(
                    title = "Auto-avanzar la cola",
                    subtitle = "Instala el siguiente PKG al terminar",
                    checked = form.autoAdvance,
                    onCheckedChange = { value ->
                        viewModel.updateForm { it.copy(autoAdvance = value) }
                    },
                )
                SettingSwitchRow(
                    title = "Escanear subcarpetas",
                    subtitle = "Al importar una carpeta, busca PKG en subdirectorios",
                    checked = form.scanSubdirectories,
                    onCheckedChange = { value ->
                        viewModel.updateForm { it.copy(scanSubdirectories = value) }
                    },
                )
                SettingSwitchRow(
                    title = "Leer metadatos del PKG",
                    subtitle = "Extrae título, Content ID e icono del paquete fPKG",
                    checked = form.readMetadata,
                    onCheckedChange = { value ->
                        viewModel.updateForm { it.copy(readMetadata = value) }
                    },
                )
                SettingSwitchRow(
                    title = "Escanear carpeta al iniciar",
                    subtitle = "Sincroniza automáticamente la carpeta vinculada al abrir la app",
                    checked = form.autoScanOnStartup,
                    onCheckedChange = { value ->
                        viewModel.updateForm { it.copy(autoScanOnStartup = value) }
                    },
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving,
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                }
                Text(
                    text = if (state.saving) "Guardando…" else "Guardar configuración",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Text(
                text = "Nota: los cambios de puerto o interfaz reinician el servidor. " +
                    "Guarda antes de comprobar la conexión.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
