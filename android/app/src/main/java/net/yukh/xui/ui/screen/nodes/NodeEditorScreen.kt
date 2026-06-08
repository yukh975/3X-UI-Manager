package net.yukh.xui.ui.screen.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
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
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeEditorScreen(
    state: NodeEditorState,
    onName: (String) -> Unit,
    onRemark: (String) -> Unit,
    onScheme: (String) -> Unit,
    onAddress: (String) -> Unit,
    onPort: (String) -> Unit,
    onBasePath: (String) -> Unit,
    onApiToken: (String) -> Unit,
    onEnable: (Boolean) -> Unit,
    onAllowPrivate: (Boolean) -> Unit,
    onTlsVerifyMode: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) tr("Add node") else tr("Edit node")) },
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
        bottomBar = {
            if (!state.isNew) {
                Surface(tonalElevation = 3.dp) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("  " + tr("Delete node"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
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
                value = state.name,
                onValueChange = onName,
                label = { Text(tr("Name")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.remark,
                onValueChange = onRemark,
                label = { Text(tr("Remark (optional)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SchemeDropdown(
                    value = state.scheme,
                    onSelect = onScheme,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.port,
                    onValueChange = onPort,
                    label = { Text(tr("Port")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = (state.port.toIntOrNull() ?: 0) !in 1..65535,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.address,
                onValueChange = onAddress,
                label = { Text(tr("Address (IP or domain)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.basePath,
                onValueChange = onBasePath,
                label = { Text(tr("Base path")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.apiToken,
                onValueChange = onApiToken,
                label = { Text(tr("API token")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(tr("From the node panel: Settings → Security → API Token")) },
            )

            SchemeDropdown(
                value = state.tlsVerifyMode,
                onSelect = onTlsVerifyMode,
                label = tr("TLS verify"),
                options = listOf("verify", "skip", "pin"),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr("Allow private address"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        tr("Permit RFC1918 / LAN addresses"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.allowPrivateAddress, onCheckedChange = onAllowPrivate)
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (confirmSave) {
        ConfirmDialog(
            title = if (state.isNew) tr("Add node?") else tr("Save changes?"),
            text = if (state.isNew) "Add node \"${state.name}\"?"
            else "Apply changes to \"${state.name}\"?",
            confirmLabel = if (state.isNew) tr("Add") else tr("Save"),
            onConfirm = onSave,
            onDismiss = { confirmSave = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Delete node?")) },
            text = { Text(tr("The remote panel itself is untouched; it's just removed from this list.")) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel")) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchemeDropdown(
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = tr("Scheme"),
    options: List<String> = listOf("https", "http"),
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}
