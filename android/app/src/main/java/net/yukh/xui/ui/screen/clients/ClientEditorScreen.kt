package net.yukh.xui.ui.screen.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import net.yukh.xui.ui.format.formatDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClientEditorScreen(
    state: ClientEditorState,
    onEmail: (String) -> Unit,
    onEnable: (Boolean) -> Unit,
    onLimitIp: (String) -> Unit,
    onTotalGb: (String) -> Unit,
    onReset: (String) -> Unit,
    onTgId: (String) -> Unit,
    onGroup: (String) -> Unit,
    onComment: (String) -> Unit,
    onExpiry: (Long) -> Unit,
    onToggleInbound: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New client" else "Edit client") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = state.canSave) {
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
                value = state.email,
                onValueChange = onEmail,
                label = { Text("Email / name") },
                singleLine = true,
                enabled = state.isNew, // email is the identity key; rename out of scope
                modifier = Modifier.fillMaxWidth(),
                supportingText = if (!state.isNew) {
                    { Text("Email can't be changed here") }
                } else null,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.enable, onCheckedChange = onEnable)
            }

            HorizontalDivider()

            // Inbound membership — editable on create, shown on edit.
            Text("Inbounds", style = MaterialTheme.typography.titleSmall)
            if (state.inboundsLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.availableInbounds.forEach { ib ->
                        val selected = ib.id in state.selectedInboundIds
                        FilterChip(
                            selected = selected,
                            onClick = { if (state.isNew) onToggleInbound(ib.id) },
                            enabled = state.isNew,
                            label = {
                                Text(ib.remark.ifBlank { "#${ib.id}" } + " · ${ib.protocol}")
                            },
                        )
                    }
                }
                if (!state.isNew) {
                    Text(
                        "Attach/detach from inbounds isn't editable here yet.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = state.totalGb,
                onValueChange = onTotalGb,
                label = { Text("Traffic limit (GB, 0 = unlimited)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.limitIp,
                onValueChange = onLimitIp,
                label = { Text("IP limit (0 = unlimited)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.reset,
                onValueChange = onReset,
                label = { Text("Traffic reset period (days, 0 = off)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
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

            OutlinedTextField(
                value = state.tgId,
                onValueChange = onTgId,
                label = { Text("Telegram ID (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.group,
                onValueChange = onGroup,
                label = { Text("Group (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.comment,
                onValueChange = onComment,
                label = { Text("Comment (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (showDatePicker) {
        ExpiryDatePickerDialog(
            initialMillis = state.expiryTime.takeIf { it > 0 },
            onPick = { onExpiry(it); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryDatePickerDialog(
    initialMillis: Long?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { pickerState.selectedDateMillis?.let(onPick) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        androidx.compose.material3.DatePicker(state = pickerState)
    }
}
