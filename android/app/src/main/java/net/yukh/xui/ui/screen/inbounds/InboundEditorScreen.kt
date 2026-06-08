package net.yukh.xui.ui.screen.inbounds

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
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
import net.yukh.xui.data.json.bool
import net.yukh.xui.data.json.child
import net.yukh.xui.data.json.string
import net.yukh.xui.data.json.strings
import net.yukh.xui.ui.components.ConfirmDialog
import net.yukh.xui.ui.format.formatDate

private val NETWORKS = listOf("tcp", "ws", "grpc", "httpupgrade", "xhttp", "kcp")
private val SECURITIES = listOf("none", "tls", "reality")
private val FINGERPRINTS = listOf("chrome", "firefox", "safari", "ios", "android", "edge", "random", "randomized")
private val SNIFF_TARGETS = listOf("http", "tls", "quic", "fakedns")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboundEditorScreen(
    state: InboundEditorState,
    vm: InboundsViewModel,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New inbound" else "Edit inbound") },
                navigationIcon = {
                    IconButton(onClick = vm::closeEditor) {
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
            // ---- Basics ----
            OutlinedTextField(
                value = state.remark,
                onValueChange = vm::setEditorRemark,
                label = { Text("Remark") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow("Enabled", state.enable, vm::setEditorEnable)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.port,
                    onValueChange = vm::setEditorPort,
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = (state.port.toIntOrNull() ?: 0) !in 1..65535,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.listen,
                    onValueChange = vm::setEditorListen,
                    label = { Text("Listen IP (blank = all)") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                )
            }
            LabeledDropdown("Protocol", state.protocol, InboundTemplates.PROTOCOLS, state.isNew, vm::setEditorProtocol)

            HorizontalDivider()

            // ---- Limits ----
            OutlinedTextField(
                value = state.totalGb,
                onValueChange = vm::setEditorTotalGb,
                label = { Text("Traffic limit (GB, 0 = unlimited)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledDropdown("Traffic reset", state.trafficReset, InboundTemplates.TRAFFIC_RESET, true, vm::setEditorTrafficReset)
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
                    OutlinedButton(onClick = { vm.setEditorExpiry(0) }) { Text("Never") }
                }
                androidx.compose.material3.Button(onClick = { showDatePicker = true }) { Text("Pick date") }
            }

            HorizontalDivider()

            // ---- Transport ----
            SectionTitle("Transport")
            LabeledDropdown("Network", state.network, NETWORKS, true, vm::setNetwork)
            when (state.network) {
                "ws" -> {
                    val ws = state.stream.child("wsSettings")
                    Field("Path", ws.string("path"), vm::setWsPath)
                    Field("Host", ws.string("host"), vm::setWsHost)
                }
                "httpupgrade" -> {
                    val hu = state.stream.child("httpupgradeSettings")
                    Field("Path", hu.string("path"), vm::setHttpPath)
                    Field("Host", hu.string("host"), vm::setHttpHost)
                }
                "grpc" -> {
                    val g = state.stream.child("grpcSettings")
                    Field("Service name", g.string("serviceName"), vm::setGrpcService)
                }
            }

            HorizontalDivider()

            // ---- Security ----
            SectionTitle("Security")
            LabeledDropdown("Security", state.security, SECURITIES, true, vm::setSecurity)
            when (state.security) {
                "tls" -> {
                    val tls = state.stream.child("tlsSettings")
                    Field("SNI (server name)", tls.string("serverName"), vm::setTlsServerName)
                }
                "reality" -> {
                    val r = state.stream.child("realitySettings")
                    val rs = r.child("settings")
                    Field("Dest (target)", r.string("dest"), vm::setRealityDest)
                    Field("Server names (comma-separated)", r.strings("serverNames").joinToString(", "), vm::setRealityServerNames)
                    Field("Short IDs (comma-separated)", r.strings("shortIds").joinToString(", "), vm::setRealityShortIds)
                    LabeledDropdown("Fingerprint", rs.string("fingerprint").ifBlank { "chrome" }, FINGERPRINTS, true, vm::setRealityFingerprint)
                    Field("Public key", rs.string("publicKey"), vm::setRealityPublicKey)
                    Field("Private key", r.string("privateKey"), vm::setRealityPrivateKey)
                }
            }

            HorizontalDivider()

            // ---- Sniffing ----
            SectionTitle("Sniffing")
            SwitchRow("Enabled", state.sniffing.bool("enabled"), vm::setSniffEnabled)
            val dest = state.sniffing.strings("destOverride")
            SNIFF_TARGETS.forEach { target ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { vm.toggleDestOverride(target) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = target in dest, onCheckedChange = { vm.toggleDestOverride(target) })
                    Text(target, style = MaterialTheme.typography.bodyLarge)
                }
            }

            HorizontalDivider()

            // ---- Advanced (protocol settings, clients excluded) ----
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Advanced: protocol settings (JSON)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(if (showAdvanced) "Hide" else "Show", color = MaterialTheme.colorScheme.primary)
            }
            if (showAdvanced) {
                Text(
                    "Clients are managed on the Clients tab and are kept as-is.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.settingsText,
                    onValueChange = vm::setEditorSettings,
                    label = { Text("settings") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (!state.isNew) {
                HorizontalDivider()
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
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
            onConfirm = vm::saveEditor,
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
                    pickerState.selectedDateMillis?.let(vm::setEditorExpiry)
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
                    vm.deleteInbound(state.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
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
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}
