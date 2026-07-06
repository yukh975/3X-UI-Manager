package net.yukh.xui.ui.screen.inbounds

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.data.api.dto.InboundTemplates
import net.yukh.xui.data.api.dto.VlessEncAuth
import net.yukh.xui.data.json.asObject
import net.yukh.xui.data.json.bool
import net.yukh.xui.data.json.child
import net.yukh.xui.data.json.putString
import net.yukh.xui.data.json.string
import net.yukh.xui.data.json.strings
import net.yukh.xui.i18n.LocalAppLanguage
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.ConfirmDialog
import net.yukh.xui.ui.format.formatDate

private val keygenJson = Json { prettyPrint = true; isLenient = true }

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
    var showStreamAdvanced by remember { mutableStateOf(false) }
    var showVlessKeygen by remember { mutableStateOf(false) }
    val invalidJsonMsg = tr("Invalid JSON")

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) tr("New inbound") else tr("Edit inbound")) },
                navigationIcon = {
                    IconButton(onClick = vm::closeEditor) {
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
            if (!state.isNew && !state.loading) {
                Surface(tonalElevation = 3.dp) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("  " + tr("Delete inbound"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Basics ----
            OutlinedTextField(
                value = state.remark,
                onValueChange = vm::setEditorRemark,
                label = { Text(tr("Remark")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(tr("Enabled"), state.enable, vm::setEditorEnable)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.port,
                    onValueChange = vm::setEditorPort,
                    label = { Text(tr("Port")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = (state.port.toIntOrNull() ?: 0) !in 1..65535,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.listen,
                    onValueChange = vm::setEditorListen,
                    label = { Text(tr("Listen IP (blank = all)")) },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                )
            }
            if (state.port == "62789") {
                Text(
                    tr("Port 62789 is the reserved internal Xray API port — the panel may reject it."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // Reachability monitoring — only for a saved inbound (needs a real port).
            if (!state.isNew) {
                SwitchRow(tr("Monitor reachability"), state.monitored, vm::setEditorMonitored)
                Text(
                    tr("Panel alerts notify you if this inbound's port stops answering (for inbounds exposed directly, without a reverse proxy)."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LabeledDropdown(tr("Protocol"), state.protocol, InboundTemplates.PROTOCOLS, state.isNew, vm::setEditorProtocol)

            HorizontalDivider()

            // ---- Limits ----
            OutlinedTextField(
                value = state.totalGb,
                onValueChange = vm::setEditorTotalGb,
                label = { Text(tr("Traffic limit (GB, 0 = unlimited)")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledDropdown(tr("Traffic reset"), state.trafficReset, InboundTemplates.TRAFFIC_RESET, true, vm::setEditorTrafficReset)
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
                    OutlinedButton(onClick = { vm.setEditorExpiry(0) }) { Text(tr("Never")) }
                }
                androidx.compose.material3.Button(onClick = { showDatePicker = true }) { Text(tr("Pick date")) }
            }

            HorizontalDivider()

            // ---- Transport ----
            SectionTitle(tr("Transport"))
            LabeledDropdown(tr("Network"), state.network, NETWORKS, true, vm::setNetwork)
            when (state.network) {
                "ws" -> {
                    val ws = state.stream.child("wsSettings")
                    Field(tr("Path"), ws.string("path"), vm::setWsPath)
                    Field(tr("Host"), ws.string("host"), vm::setWsHost)
                }
                "httpupgrade" -> {
                    val hu = state.stream.child("httpupgradeSettings")
                    Field(tr("Path"), hu.string("path"), vm::setHttpPath)
                    Field(tr("Host"), hu.string("host"), vm::setHttpHost)
                }
                "grpc" -> {
                    val g = state.stream.child("grpcSettings")
                    Field(tr("Service name"), g.string("serviceName"), vm::setGrpcService)
                }
            }

            HorizontalDivider()

            // ---- Security ----
            SectionTitle(tr("Security"))
            LabeledDropdown(tr("Security"), state.security, SECURITIES, true, vm::setSecurity)
            when (state.security) {
                "tls" -> {
                    val tls = state.stream.child("tlsSettings")
                    Field(tr("SNI (server name)"), tls.string("serverName"), vm::setTlsServerName)
                }
                "reality" -> {
                    val r = state.stream.child("realitySettings")
                    val rs = r.child("settings")
                    Field(tr("Dest (target)"), r.string("dest"), vm::setRealityDest)
                    Field(tr("Server names (comma-separated)"), r.strings("serverNames").joinToString(", "), vm::setRealityServerNames)
                    Field(tr("Short IDs (comma-separated)"), r.strings("shortIds").joinToString(", "), vm::setRealityShortIds)
                    LabeledDropdown(tr("Fingerprint"), rs.string("fingerprint").ifBlank { "chrome" }, FINGERPRINTS, true, vm::setRealityFingerprint)
                    Field(tr("Public key"), rs.string("publicKey"), vm::setRealityPublicKey)
                    Field(tr("Private key"), r.string("privateKey"), vm::setRealityPrivateKey)
                }
            }

            // Raw streamSettings escape hatch for advanced TLS/REALITY/sockopt/XHTTP
            // fields not exposed above (Verify Peer Cert By Name, Limit Fallback,
            // Session ID Table/Length, Real client IP, FinalMask, …).
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showStreamAdvanced = !showStreamAdvanced },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Advanced: stream settings (JSON)"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(if (showStreamAdvanced) tr("Hide") else tr("Show"), color = MaterialTheme.colorScheme.primary)
            }
            if (showStreamAdvanced) {
                var streamText by remember(showStreamAdvanced) {
                    mutableStateOf(keygenJson.encodeToString(JsonElement.serializer(), state.stream))
                }
                var streamErr by remember(showStreamAdvanced) { mutableStateOf<String?>(null) }
                OutlinedTextField(
                    value = streamText,
                    onValueChange = { txt ->
                        streamText = txt
                        runCatching { keygenJson.parseToJsonElement(txt).asObject() }
                            .onSuccess { streamErr = null; vm.setEditorStreamRaw(it) }
                            .onFailure { streamErr = invalidJsonMsg }
                    },
                    label = { Text("streamSettings") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                )
                streamErr?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }

            HorizontalDivider()

            // ---- Sniffing ----
            SectionTitle(tr("Sniffing"))
            SwitchRow(tr("Enabled"), state.sniffing.bool("enabled"), vm::setSniffEnabled)
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

            if (state.protocol == "vless") {
                OutlinedButton(
                    onClick = { showVlessKeygen = true; vm.loadVlessKeys() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(tr("Generate VLESS encryption key")) }
            }

            // ---- Advanced (protocol settings, clients excluded) ----
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Advanced: protocol settings (JSON)"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(if (showAdvanced) tr("Hide") else tr("Show"), color = MaterialTheme.colorScheme.primary)
            }
            if (showAdvanced) {
                Text(
                    tr("Clients are managed on the Clients tab and are kept as-is."),
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
        }
    }

    if (confirmSave) {
        ConfirmDialog(
            title = if (state.isNew) tr("Create inbound?") else tr("Save changes?"),
            text = if (state.isNew) "Create this inbound on port ${state.port}?"
            else tr("Apply changes to this inbound? Xray will restart."),
            confirmLabel = if (state.isNew) tr("Create") else tr("Save"),
            onConfirm = vm::saveEditor,
            onDismiss = { confirmSave = false },
        )
    }

    if (showVlessKeygen) {
        VlessKeygenDialog(
            keys = state.vlessKeys,
            loading = state.vlessKeysLoading,
            error = state.vlessKeysError,
            onInsertDecryption = { dec ->
                val parsed = runCatching { keygenJson.parseToJsonElement(state.settingsText).asObject() }
                    .getOrNull() ?: JsonObject(emptyMap())
                vm.setEditorSettings(keygenJson.encodeToString(JsonElement.serializer(), parsed.putString("decryption", dec)))
                showVlessKeygen = false
                vm.clearVlessKeys()
            },
            onDismiss = { showVlessKeygen = false; vm.clearVlessKeys() },
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
                }) { Text(tr("OK")) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(tr("Cancel")) } },
        ) { androidx.compose.material3.DatePicker(state = pickerState) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Delete inbound?")) },
            text = { Text(tr("This removes the inbound and all its clients. This can't be undone.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteInbound(state.id)
                }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel")) } },
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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

@Composable
private fun VlessKeygenDialog(
    keys: List<VlessEncAuth>?,
    loading: Boolean,
    error: String?,
    onInsertDecryption: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(keys) { mutableStateOf(keys?.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("VLESS encryption key")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                    keys.isNullOrEmpty() -> Text(tr("No keys returned."))
                    else -> {
                        LabeledDropdown(tr("Key generation"), selected?.label ?: "", keys.map { it.label }, true) { lbl ->
                            selected = keys.firstOrNull { it.label == lbl }
                        }
                        selected?.let { a ->
                            KeyField(tr("Decryption"), a.decryption)
                            KeyField(tr("Encryption"), a.encryption)
                            Text(
                                tr("Decryption goes on this inbound; encryption goes to the clients."),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val sel = selected
            TextButton(onClick = { sel?.let { onInsertDecryption(it.decryption) } }, enabled = sel != null) {
                Text(tr("Insert decryption"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
    )
}

@Composable
private fun KeyField(label: String, value: String) {
    val context = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText(label, value))
            }) { Icon(Icons.Outlined.ContentCopy, contentDescription = tr("Copy"), modifier = Modifier.size(18.dp)) }
        }
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
    }
}
