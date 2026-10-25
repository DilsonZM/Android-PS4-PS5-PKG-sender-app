package com.remotesend.app.ui.screens.settings

import android.Manifest
import android.net.Uri
import android.provider.OpenableColumns
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remotesend.app.core.AppLinks
import com.remotesend.app.domain.model.FrameRateMode
import com.remotesend.app.domain.model.ThemeMode
import com.remotesend.app.ui.CollectUiMessages
import com.remotesend.app.ui.appViewModel
import com.remotesend.app.ui.components.ConfirmDialog
import com.remotesend.app.ui.components.InfoRow
import com.remotesend.app.ui.components.ScreenTopBar
import com.remotesend.app.ui.components.SectionCard
import com.remotesend.app.ui.components.SettingSwitchRow
import com.remotesend.app.ui.frameRateLabel
import com.remotesend.app.ui.themeModeLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onOpenLogs: () -> Unit,
) {
    val viewModel: SettingsViewModel = appViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CollectUiMessages(viewModel.events)
    val context = LocalContext.current
    val settings = state.settings

    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var confirmClearLogs by remember { mutableStateOf(false) }
    var ftpUri by remember { mutableStateOf<Uri?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }
    val ftpPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        ftpUri = uri
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "Ajustes",
            subtitle = "Apariencia, notificaciones y copias de seguridad",
            actions = {
                IconButton(onClick = onOpenLogs) {
                    Icon(Icons.Outlined.BugReport, contentDescription = "Registros")
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
            SectionCard(title = "Apariencia") {
                Text("Tema", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(themeModeLabel(mode)) },
                        )
                    }
                }
                Text("Frecuencia de actualización", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FrameRateMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.frameRateMode == mode,
                            onClick = { viewModel.setFrameRate(mode) },
                            label = { Text(frameRateLabel(mode)) },
                        )
                    }
                }
                Text(
                    text = "Solicita 60 o 120 Hz al sistema. Depende de la pantalla del " +
                        "dispositivo y 120 FPS aumenta el consumo de batería.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingSwitchRow(
                    title = "Color dinámico",
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "Usa la paleta del sistema (Material You)"
                    } else {
                        "Disponible desde Android 12"
                    },
                    checked = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            SectionCard(title = "Notificaciones") {
                SettingSwitchRow(
                    title = "Notificaciones de instalación",
                    subtitle = "Progreso, finalización y errores de la cola",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setNotifications(enabled)
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !notificationsGranted
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
                if (!notificationsGranted) {
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Permitir notificaciones", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Text(
                    text = "Android muestra una notificación permanente mientras el servidor " +
                        "está en primer plano. Es necesaria para que la consola pueda descargar " +
                        "los PKG con la pantalla apagada.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(
                title = "Copias de seguridad",
                subtitle = "Exporta o restaura la cola en JSON",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val name = "remotesend-backup-${
                                SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                            }.json"
                            exportBackup.launch(name)
                        },
                        enabled = !state.backupBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Exportar", modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            importBackup.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        enabled = !state.backupBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Importar", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                if (state.backupBusy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Procesando copia de seguridad…")
                    }
                }
                state.backupMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "El JSON se puede guardar en Google Drive u otro proveedor desde " +
                        "el selector de archivos. Las rutas locales no se transfieren entre " +
                        "dispositivos: al importar se emparejan los PKG ya presentes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(
                title = "Enviar archivo a PS4",
                subtitle = "FTP GoldHEN · copia sin instalar",
            ) {
                Text(
                    text = "Destino: /user/data/RemoteSend",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Consola: ${state.consoleHost.ifBlank { "sin configurar" }} · puerto 2121",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { ftpPicker.launch(arrayOf("*/*")) },
                    enabled = !state.ftpBusy,
                ) {
                    Icon(
                        Icons.Outlined.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Elegir archivo", modifier = Modifier.padding(start = 6.dp))
                }
                val selectedFtp = ftpUri
                if (selectedFtp != null) {
                    Text(
                        text = "Seleccionado: ${fileDisplayName(context, selectedFtp)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = { selectedFtp?.let(viewModel::uploadToPs4) },
                    enabled = selectedFtp != null && !state.ftpBusy &&
                        state.consoleHost.isNotBlank(),
                ) {
                    Icon(
                        Icons.Outlined.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Subir por FTP", modifier = Modifier.padding(start = 6.dp))
                }
                if (state.ftpBusy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Enviando archivo…")
                    }
                }
                state.ftpMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.ftpRemotePath?.let { path ->
                    Text(
                        text = "Ruta en PS4: $path",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "El archivo se copia al almacenamiento interno de la PS4; " +
                        "no se instala ni se ejecuta.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Datos y depuración") {
                SettingSwitchRow(
                    title = "Modo desarrollador",
                    subtitle = "Muestra información técnica y opciones de depuración",
                    checked = settings.developerMode,
                    onCheckedChange = viewModel::setDeveloperMode,
                )
                OutlinedCard(
                    onClick = onOpenLogs,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Outlined.BugReport, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Registros", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "${state.logsCount} entrada(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onOpenLogs) {
                            Text("Abrir")
                        }
                    }
                }
                OutlinedButton(
                    onClick = { confirmClearLogs = true },
                    enabled = state.logsCount > 0,
                ) {
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Vaciar registros", modifier = Modifier.padding(start = 6.dp))
                }
            }

            SectionCard(title = "Creador") {
                val uriHandler = LocalUriHandler.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp).size(26.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dilson Zuleta Molina",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Ingeniero Electrónico · Visual Analytics & Big Data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "Especialista en Power Platform (Power BI, Power Apps, Power Automate), " +
                        "Python y SQL. Más de 6 años liderando soluciones de analítica, " +
                        "automatización y gestión de datos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = { uriHandler.openUri("https://github.com/DilsonZM") },
                    ) {
                        Text("github.com/DilsonZM")
                    }
                    TextButton(
                        onClick = { uriHandler.openUri(AppLinks.PAYPAL_SUPPORT_URL) },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocalCafe,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Invítame un café",
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }

            SectionCard(title = "Acerca de RemoteSend") {
                InfoRow("Versión", state.appVersion)
                InfoRow("Consolas", "PS4 (flatZ RPI / GoldHEN) y PS5 (etaHEN)")
                Text(
                    text = "RemoteSend es una reimplementación Android inspirada en " +
                        "PS4 Remote PKG Sender V2 de Gkiokan. Sirve archivos PKG desde el " +
                        "almacenamiento del teléfono y ordena su instalación mediante las " +
                        "APIs de homebrew. No está afiliado a Sony Interactive Entertainment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "La consola debe poder acceder al teléfono por la red local. " +
                        "Si usas datos móviles o una red invitada con aislamiento de clientes, " +
                        "la descarga fallará.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenLogs) {
                        Icon(
                            Icons.Outlined.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Ver registros", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }

    if (confirmClearLogs) {
        ConfirmDialog(
            title = "Vaciar registros",
            text = "Se eliminarán todos los registros de depuración guardados.",
            confirmLabel = "Vaciar",
            onConfirm = viewModel::clearLogs,
            onDismiss = { confirmClearLogs = false },
        )
    }
}


private fun fileDisplayName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                cursor.getString(index)?.let { return it }
            }
        }
    }
    return uri.lastPathSegment ?: "archivo"
}
