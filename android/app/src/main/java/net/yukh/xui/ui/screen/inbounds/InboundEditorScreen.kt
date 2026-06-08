package net.yukh.xui.ui.screen.inbounds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.yukh.xui.data.api.dto.InboundTemplates
import net.yukh.xui.ui.components.ConfirmDialog
import net.yukh.xui.ui.format.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboundEditorScreen(
    state: InboundEditorState,
    onRemark: (String) -> Unit,
    onEnable: (Boolean) -> Unit,
    onListen: (String) -> Unit,
    onPort: (String) -> Unit,
    onProtocol: (String) -> Unit,
    onTotalGb: (String) -> Unit,
    onExpiry: (Long) -> Unit,
    onTrafficReset: (String) -> Unit,
    onSettings: (String) -> Unit,
    onStream: (String) -> Unit,
    onSniffing: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New inbound" else "Edit inbound") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = { confirmSave = true }, enabled = state.canSave) {
                        if (state.saving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.remark,
                onValueChange = onRemark,
                label = { Text("Remark") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.enable, onCheckedChange = onEnable)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.port,
                    onValueChange = onPort,
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    isError = (state.port.toIntOrNull() ?: 0) !in 1..65535,
                )
                OutlinedTextField(
                    value = state.listen,
                    onValueChange = onListen,
                    label = { Text("Listen IP (blank = all)") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                )
            }

            LabeledDropdown(
                label = "Protocol",
                value = state.protocol,
                options = InboundTemplates.PROTOCOLS,
                enabled = state.isNew, // changing protocol of an existing inbound is rarely safe
                onSelect = onProtocol,
            )

            OutlinedTextField(
                value = state.totalGb,
                onValueChange = onTotalGb,
                label = { Text("Traffic limit (GB, 0 = unlimited)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            LabeledDropdown(
                label = "Traffic reset",
                value = state.trafficReset,
                options = InboundTemplates.TRAFFIC_RESET,
                enabled = true,
                onSelect = onTrafficReset,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Expiry", style = MaterialTheme.typography.labelMedium)
                    Text(state.expiryTime.formatDate(), style = MaterialTheme.typography.bodyLarge)
                }
                if (state.expiryTime != 0L) {
                    OutlinedButton(onClick = { onExpiry(0) }) { Text("Never") }
                }
                Button(onClick = { showDatePicker = true }) { Text("Pick date") }
            }

            HorizontalDivider()
            Text("Advanced (raw JSON)", style = MaterialTheme.typography.titleSmall)

            JsonField(label = "settings", value = state.settingsText, onValueChange = onSettings)
            JsonField(label = "streamSettings", value = state.streamText, onValueChange = onStream)
            JsonField(label = "sniffing", value = state.sniffingText, onValueChange = onSniffing)

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (!state.isNew) {
                HorizontalDivider()
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("  Delete inbound", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmSave) {
        ConfirmDialog(
            title = if (state.isNew) "Create inbound?" else "Save changes?",
            text = if (state.isNew) "Create this inbound on port ${state.port}?"
            else "Apply changes to this inbound? Xray will restart.",
            confirmLabel = if (state.isNew) "Create" else "Save",
            onConfirm = onSave,
            onDismiss = { confirmSave = false },
        )
    }

    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = state.expiryTime.takeIf { it > 0 },
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let(onExpiry)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { androidx.compose.material3.DatePicker(state = pickerState) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete inbound?") },
            text = { Text("This removes the inbound and all its clients. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun JsonField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        textStyle = MaterialTheme.typography.bodySmall,
    )
}
