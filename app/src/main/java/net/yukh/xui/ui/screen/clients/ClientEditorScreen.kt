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
import androidx.compose.material.icons.filled.Refresh
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
import net.yukh.xui.i18n.LocalAppLanguage
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.ConfirmDialog
import net.yukh.xui.ui.components.EditableDropdownField
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
    onAdTag: (String) -> Unit,
    onRegenerateSecret: () -> Unit,
    onExpiry: (Long) -> Unit,
    onToggleInbound: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) tr("New client") else tr("Edit client")) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Cancel"))
                    }
                },
                actions = {
                    TextButton(onClick = { confirmSave = true }, enabled = state.canSave) {
                        if (state.saving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(tr("Save"))
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmail,
                label = { Text(tr("Email / name")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Enabled"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = state.enable, onCheckedChange = onEnable)
            }

            HorizontalDivider()

            // Inbound membership — editable on both create and edit (the toggles
            // diff against the client's current inbounds and attach/detach on save).
            Text(tr("Inbounds"), style = MaterialTheme.typography.titleSmall)
            if (state.inboundsLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.availableInbounds.forEach { ib ->
                        val selected = ib.id in state.selectedInboundIds
                        FilterChip(
                            selected = selected,
                            onClick = { onToggleInbound(ib.id) },
                            label = {
                                Text(ib.remark.ifBlank { "#${ib.id}" } + " · ${ib.protocol}")
                            },
                        )
                    }
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = state.totalGb,
                onValueChange = onTotalGb,
                label = { Text(tr("Traffic limit (GB, 0 = unlimited)")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.limitIp,
                onValueChange = onLimitIp,
                label = { Text(tr("IP limit (0 = unlimited)")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.reset,
                onValueChange = onReset,
                label = { Text(tr("Traffic reset period (days, 0 = off)")) },
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
                    Text(tr("Expiry"), style = MaterialTheme.typography.labelMedium)
                    Text(state.expiryTime.formatDate(LocalAppLanguage.current), style = MaterialTheme.typography.bodyLarge)
                }
                if (state.expiryTime != 0L) {
                    OutlinedButton(onClick = { onExpiry(0) }) { Text(tr("Never")) }
                }
                Button(onClick = { showDatePicker = true }) { Text(tr("Pick date")) }
            }

            OutlinedTextField(
                value = state.tgId,
                onValueChange = onTgId,
                label = { Text(tr("Telegram ID (optional)")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            EditableDropdownField(
                label = tr("Group (optional)"),
                value = state.group,
                options = state.availableGroups,
                onChange = onGroup,
            )

            OutlinedTextField(
                value = state.comment,
                onValueChange = onComment,
                label = { Text(tr("Comment (optional)")) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isMtproto) {
                OutlinedTextField(
                    value = state.secret,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(tr("MTProto secret")) },
                    trailingIcon = {
                        IconButton(onClick = onRegenerateSecret) {
                            Icon(Icons.Filled.Refresh, contentDescription = tr("Regenerate"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.adTag,
                    onValueChange = onAdTag,
                    label = { Text(tr("Ad-tag (sponsored channel, 32 hex)")) },
                    isError = state.adTag.isNotEmpty() && state.adTag.length != 32,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (confirmSave) {
        ConfirmDialog(
            title = if (state.isNew) tr("Create client?") else tr("Save changes?"),
            text = if (state.isNew) "${tr("Create client")} \"${state.email}\"?"
            else "${tr("Apply changes to")} \"${state.email}\"?",
            confirmLabel = if (state.isNew) tr("Create") else tr("Save"),
            onConfirm = onSave,
            onDismiss = { confirmSave = false },
        )
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
            TextButton(onClick = { pickerState.selectedDateMillis?.let(onPick) }) { Text(tr("OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    ) {
        androidx.compose.material3.DatePicker(state = pickerState)
    }
}
