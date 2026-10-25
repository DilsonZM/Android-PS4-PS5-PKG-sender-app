package com.remotesend.app.ui.screens.logs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remotesend.app.domain.model.LogEntry
import com.remotesend.app.domain.model.LogLevel
import com.remotesend.app.ui.CollectUiMessages
import com.remotesend.app.ui.appViewModel
import com.remotesend.app.ui.components.ConfirmDialog
import com.remotesend.app.ui.components.EmptyState
import com.remotesend.app.ui.components.ScreenTopBar
import com.remotesend.app.ui.formatTimestamp
import com.remotesend.app.ui.logLevelLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(
    onBack: () -> Unit,
) {
    val viewModel: LogsViewModel = appViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CollectUiMessages(viewModel.events)
    var confirmClear by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) viewModel.exportLogs(uri)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "Registros",
            subtitle = "${state.logs.size} de ${state.totalCount} entrada(s)",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        val name = "remotesend-logs-${
                            SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                        }.txt"
                        exportLauncher.launch(name)
                    },
                ) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = "Exportar registros")
                }
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "Vaciar registros")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogLevel.entries.forEach { level ->
                    FilterChip(
                        selected = level in state.enabledLevels,
                        onClick = { viewModel.toggleLevel(level) },
                        label = { Text(logLevelLabel(level)) },
                    )
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Filtrar registros") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Limpiar")
                        }
                    }
                },
            )
        }

        if (state.logs.isEmpty()) {
            EmptyState(
                title = "Sin registros",
                message = "Aquí aparecerán los eventos del servidor, la detección y las instalaciones.",
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.logs, key = { it.id }) { entry ->
                LogRow(entry)
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Vaciar registros",
            text = "Se eliminarán todas las entradas guardadas.",
            confirmLabel = "Vaciar",
            onConfirm = viewModel::clearLogs,
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val color = logColor(entry.level)

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.details != null) { expanded = !expanded },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Text(
                    text = logLevelLabel(entry.level),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Text(
                    text = entry.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
            )
            if (expanded && entry.details != null) {
                Text(
                    text = entry.details,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun logColor(level: LogLevel): Color {
    return when (level) {
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.SUCCESS -> Color(0xFF2E7D32)
        LogLevel.WARN -> Color(0xFFB26A00)
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
}
