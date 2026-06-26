package net.yukh.xui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.yukh.xui.shared.dto.InboundModel
import net.yukh.xui.shared.dto.InboundTemplates
import net.yukh.xui.shared.dto.VlessEncAuth
import net.yukh.xui.shared.dto.buildInbound
import net.yukh.xui.shared.dto.settingsText
import net.yukh.xui.shared.dto.sniffingText
import net.yukh.xui.shared.dto.streamSettingsText
import net.yukh.xui.shared.json.jsonGetBool
import net.yukh.xui.shared.json.jsonGetString
import net.yukh.xui.shared.json.jsonGetStrings
import net.yukh.xui.shared.json.jsonPutBool
import net.yukh.xui.shared.json.jsonPutString
import net.yukh.xui.shared.json.jsonPutStrings
import net.yukh.xui.shared.json.jsonToggleString
import net.yukh.xui.shared.json.parseCsvList

private const val GB = 1_073_741_824.0

private val NETWORKS = listOf("tcp", "ws", "grpc", "httpupgrade", "xhttp", "kcp")
private val SECURITIES = listOf("none", "tls", "reality")
private val FINGERPRINTS = listOf("chrome", "firefox", "safari", "ios", "android", "edge", "random", "randomized")
private val SNIFF_TARGETS = listOf("http", "tls", "quic", "fakedns")

/**
 * Inbound editor — create (isNew) and edit. Basics as structured fields;
 * protocol settings / transport-security / sniffing as editable JSON (mirrors
 * the Android editor's template + JSON approach). [initial] for edit is the
 * FULL inbound from getInbound, so unedited JSON round-trips intact.
 */
@Composable
fun InboundEditorScreen(
    initial: InboundModel,
    isNew: Boolean,
    saving: Boolean,
    error: String?,
    vlessEncAuths: List<VlessEncAuth>? = null,
    vlessEncLoading: Boolean = false,
    onGenVlessEnc: () -> Unit = {},
    onClearVlessEnc: () -> Unit = {},
    onSave: (InboundModel) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var protocol by remember { mutableStateOf(initial.protocol.ifBlank { "vless" }) }
    var remark by remember { mutableStateOf(initial.remark) }
    var listen by remember { mutableStateOf(initial.listen) }
    var port by remember { mutableStateOf(if (initial.port > 0) initial.port.toString() else "") }
    var enable by remember { mutableStateOf(initial.enable) }
    var trafficReset by remember { mutableStateOf(initial.trafficReset.ifBlank { "never" }) }
    var totalGb by remember { mutableStateOf(gbString(initial.total)) }
    var settingsJson by remember { mutableStateOf(if (isNew) InboundTemplates.settings(protocol) else initial.settingsText()) }
    var streamJson by remember { mutableStateOf(if (isNew) InboundTemplates.streamSettings(protocol) else initial.streamSettingsText()) }
    var sniffingJson by remember { mutableStateOf(if (isNew) InboundTemplates.sniffing() else initial.sniffingText()) }

    val portNum = port.toIntOrNull() ?: 0
    val totalBytes = totalGb.trim().replace(',', '.').toDoubleOrNull()?.let { (it * GB).toLong() } ?: 0L
    val built = buildInbound(
        initial, protocol, remark.trim(), listen.trim(), portNum, totalBytes,
        trafficReset, enable, settingsJson, streamJson, sniffingJson,
    )
    val canSave = !saving && portNum in 1..65535 && built != null

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !saving) { Text(tr("Cancel")) }
            Text(if (isNew) tr("New inbound") else tr("Edit inbound"), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { built?.let(onSave) }, enabled = canSave) {
                Text(if (saving) "…" else tr("Save"))
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)

            if (isNew) {
                Text(tr("Protocol"), style = MaterialTheme.typography.labelMedium)
                Chips(InboundTemplates.PROTOCOLS, protocol) { p ->
                    protocol = p
                    settingsJson = InboundTemplates.settings(p)
                    streamJson = InboundTemplates.streamSettings(p)
                }
            } else {
                Text("${protocol.uppercase()}  ·  #${initial.id}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            EditField(remark, { remark = it }, tr("Remark"))
            EditField(listen, { listen = it }, tr("Listen IP (blank = all)"))
            EditField(port, { port = it.filter(Char::isDigit) }, tr("Port"), KeyboardType.Number)
            EditField(totalGb, { totalGb = it }, tr("Traffic limit (GB, 0 = unlimited)"), KeyboardType.Decimal)

            Text(tr("Traffic reset"), style = MaterialTheme.typography.labelMedium)
            Chips(InboundTemplates.TRAFFIC_RESET, trafficReset) { trafficReset = it }

            EditToggle(tr("Enabled"), enable) { enable = it }

            JsonField(tr("Protocol settings (JSON)"), settingsJson) { settingsJson = it }

            // VLESS encryption keygen (panel v3.4.1, xray-core v26.6.21+): fetch the
            // panel's key options and insert the chosen decryption into settings.
            if (protocol == "vless") {
                var showVlessEnc by remember { mutableStateOf(false) }
                TextButton(onClick = { showVlessEnc = true; onGenVlessEnc() }) {
                    Text(tr("Generate VLESS encryption key"))
                }
                if (showVlessEnc) {
                    VlessEncDialog(
                        auths = vlessEncAuths,
                        loading = vlessEncLoading,
                        onInsert = { dec -> settingsJson = jsonPutString(settingsJson, listOf("decryption"), dec) },
                        onDismiss = { showVlessEnc = false; onClearVlessEnc() },
                    )
                }
            }

            // ---- Transport (structured, over streamJson) ----
            HorizontalDivider()
            SectionTitle(tr("Transport"))
            val network = jsonGetString(streamJson, listOf("network")).ifBlank { "tcp" }
            Text(tr("Network"), style = MaterialTheme.typography.labelMedium)
            Chips(NETWORKS, network) { streamJson = jsonPutString(streamJson, listOf("network"), it) }
            when (network) {
                "ws" -> {
                    EditField(jsonGetString(streamJson, listOf("wsSettings", "path")),
                        { streamJson = jsonPutString(streamJson, listOf("wsSettings", "path"), it) }, tr("Path"))
                    EditField(jsonGetString(streamJson, listOf("wsSettings", "host")),
                        { streamJson = jsonPutString(streamJson, listOf("wsSettings", "host"), it) }, tr("Host"))
                }
                "httpupgrade" -> {
                    EditField(jsonGetString(streamJson, listOf("httpupgradeSettings", "path")),
                        { streamJson = jsonPutString(streamJson, listOf("httpupgradeSettings", "path"), it) }, tr("Path"))
                    EditField(jsonGetString(streamJson, listOf("httpupgradeSettings", "host")),
                        { streamJson = jsonPutString(streamJson, listOf("httpupgradeSettings", "host"), it) }, tr("Host"))
                }
                "grpc" -> {
                    EditField(jsonGetString(streamJson, listOf("grpcSettings", "serviceName")),
                        { streamJson = jsonPutString(streamJson, listOf("grpcSettings", "serviceName"), it) }, tr("Service name"))
                }
            }

            // ---- Security (structured) ----
            HorizontalDivider()
            SectionTitle(tr("Security"))
            val security = jsonGetString(streamJson, listOf("security")).ifBlank { "none" }
            Chips(SECURITIES, security) { streamJson = jsonPutString(streamJson, listOf("security"), it) }
            when (security) {
                "tls" -> {
                    EditField(jsonGetString(streamJson, listOf("tlsSettings", "serverName")),
                        { streamJson = jsonPutString(streamJson, listOf("tlsSettings", "serverName"), it) }, tr("SNI (server name)"))
                }
                "reality" -> {
                    EditField(jsonGetString(streamJson, listOf("realitySettings", "dest")),
                        { streamJson = jsonPutString(streamJson, listOf("realitySettings", "dest"), it) }, tr("Dest (target)"))
                    var serverNames by remember {
                        mutableStateOf(jsonGetStrings(streamJson, listOf("realitySettings", "serverNames")).joinToString(", "))
                    }
                    EditField(serverNames, {
                        serverNames = it
                        streamJson = jsonPutStrings(streamJson, listOf("realitySettings", "serverNames"), parseCsvList(it))
                    }, tr("Server names (comma-separated)"))
                    var shortIds by remember {
                        mutableStateOf(jsonGetStrings(streamJson, listOf("realitySettings", "shortIds")).joinToString(", "))
                    }
                    EditField(shortIds, {
                        shortIds = it
                        streamJson = jsonPutStrings(streamJson, listOf("realitySettings", "shortIds"), parseCsvList(it))
                    }, tr("Short IDs (comma-separated)"))
                    Text(tr("Fingerprint"), style = MaterialTheme.typography.labelMedium)
                    Chips(FINGERPRINTS, jsonGetString(streamJson, listOf("realitySettings", "settings", "fingerprint")).ifBlank { "chrome" }) {
                        streamJson = jsonPutString(streamJson, listOf("realitySettings", "settings", "fingerprint"), it)
                    }
                    EditField(jsonGetString(streamJson, listOf("realitySettings", "settings", "publicKey")),
                        { streamJson = jsonPutString(streamJson, listOf("realitySettings", "settings", "publicKey"), it) }, tr("Public key"))
                    EditField(jsonGetString(streamJson, listOf("realitySettings", "privateKey")),
                        { streamJson = jsonPutString(streamJson, listOf("realitySettings", "privateKey"), it) }, tr("Private key"))
                }
            }

            // ---- Sniffing (structured, over sniffingJson) ----
            HorizontalDivider()
            SectionTitle(tr("Sniffing"))
            EditToggle(tr("Enabled"), jsonGetBool(sniffingJson, listOf("enabled"))) {
                sniffingJson = jsonPutBool(sniffingJson, listOf("enabled"), it)
            }
            val destOverride = jsonGetStrings(sniffingJson, listOf("destOverride"))
            SNIFF_TARGETS.forEach { target ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        sniffingJson = jsonToggleString(sniffingJson, listOf("destOverride"), target)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = target in destOverride, onCheckedChange = {
                        sniffingJson = jsonToggleString(sniffingJson, listOf("destOverride"), target)
                    })
                    Text(target, style = MaterialTheme.typography.bodyLarge)
                }
            }

            // ---- Advanced: raw JSON (kept in sync with the fields above) ----
            HorizontalDivider()
            JsonField(tr("Transport / security (JSON)"), streamJson) { streamJson = it }
            JsonField(tr("Sniffing (JSON)"), sniffingJson) { sniffingJson = it }
            if (built == null) {
                Text(tr("One of the JSON blocks is invalid."),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }

            if (!isNew) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDelete,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(tr("Delete inbound")) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun gbString(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes % 1_073_741_824L == 0L -> (bytes / 1_073_741_824L).toString()
    else -> ((bytes * 100 / 1_073_741_824L) / 100.0).toString()
}

@Composable
private fun Chips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt ->
            val on = opt == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    opt,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EditField(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun JsonField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = false,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** Pick one of the panel's generated VLESS-encryption key options and insert its
 *  decryption into the inbound. Each option is X25519/ML-KEM-768 × a variant; the
 *  decryption goes on this inbound, the encryption goes to the clients. */
@Composable
private fun VlessEncDialog(
    auths: List<VlessEncAuth>?,
    loading: Boolean,
    onInsert: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<VlessEncAuth?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("VLESS encryption key")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                    auths.isNullOrEmpty() -> Text(tr("No keys returned (needs panel v3.4.1, xray-core v26.6.21+)."),
                        style = MaterialTheme.typography.bodyMedium)
                    else -> auths.forEach { a ->
                        val on = a == selected
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selected = a },
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(a.label.ifBlank { a.id }, style = MaterialTheme.typography.titleSmall)
                                KeyLine(tr("Decryption"), a.decryption)
                                KeyLine(tr("Encryption"), a.encryption)
                            }
                        }
                    }
                }
                if (!auths.isNullOrEmpty()) Text(tr("Decryption goes on this inbound; encryption goes to the clients."),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let { onInsert(it.decryption) }; onDismiss() }, enabled = selected != null) {
                Text(tr("Insert decryption"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun KeyLine(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun EditToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
