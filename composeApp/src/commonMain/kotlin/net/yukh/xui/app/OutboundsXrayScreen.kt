package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch
import net.yukh.xui.shared.dto.BLACKHOLE_RESPONSE_TYPE
import net.yukh.xui.shared.dto.FORMED_PROTOCOLS
import net.yukh.xui.shared.dto.FREEDOM_DOMAIN_STRATEGY
import net.yukh.xui.shared.dto.OUTBOUND_PROTOCOLS
import net.yukh.xui.shared.dto.PROXY_PROTOCOLS
import net.yukh.xui.shared.dto.SS_METHODS
import net.yukh.xui.shared.dto.TestOutboundResult
import net.yukh.xui.shared.dto.VLESS_FLOW
import net.yukh.xui.shared.dto.VMESS_SECURITY
import net.yukh.xui.shared.dto.defaultOutbound
import net.yukh.xui.shared.dto.isValidJson
import net.yukh.xui.shared.dto.parseVlessLink
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.shared.json.jsonGetObjectList
import net.yukh.xui.shared.json.jsonGetString
import net.yukh.xui.shared.json.jsonGetStringMap
import net.yukh.xui.shared.json.jsonPutString
import net.yukh.xui.shared.json.jsonPutStringMap
import net.yukh.xui.shared.json.jsonRemove
import net.yukh.xui.shared.json.jsonSetObjectList

private val NETWORKS = listOf("tcp", "ws", "grpc", "httpupgrade", "xhttp", "kcp")
private val SECURITIES = listOf("none", "tls", "reality")

/** Structured Outbounds editor over the config's `outbounds` array. Order =
 *  priority (#1 is the default route). Each outbound edits via path helpers;
 *  a raw-JSON box covers anything the forms don't. */
@Composable
fun OutboundsXrayScreen(
    configJson: String,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    onConfigChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onTestOutbound: (suspend (String, String) -> TestOutboundResult?)? = null,
) {
    var editing by remember { mutableStateOf<Int?>(null) } // index, -1 = new
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf<Int?>(null) }

    fun list() = jsonGetObjectList(configJson, listOf("outbounds"))
    fun setList(l: List<String>) = onConfigChange(jsonSetObjectList(configJson, listOf("outbounds"), l))

    // ---- Editor view ----
    editing?.let { idx ->
        val initial = if (idx >= 0) list().getOrElse(idx) { defaultOutbound("freedom", "out") } else defaultOutbound("freedom", "out")
        OutboundEditor(
            initial = initial,
            saving = saving,
            onCancel = { editing = null },
            onDone = { obj -> val l = list().toMutableList(); if (idx >= 0) l[idx] = obj else l.add(obj); setList(l); editing = null },
        )
        return
    }

    // ---- List view ----
    Column(Modifier.fillMaxSize()) {
        XrayEditHeader(tr("Outbounds"), saving, onCancel, onSave, canSave = !loading)
        if (error != null) Text(error, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val outs = list()
            Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
                TextButton(onClick = {
                    platformPickFile { _, bytes ->
                        parseOutboundsImport(bytes.decodeToString())?.let { setList(list() + it) }
                    }
                }) { Text("⬆", style = MaterialTheme.typography.titleMedium) }
                TextButton(
                    onClick = { platformExportFile("outbounds.json", ("[" + outs.joinToString(",") + "]").encodeToByteArray()) },
                    enabled = outs.isNotEmpty(),
                ) { Text("⬇", style = MaterialTheme.typography.titleMedium) }
            }
            outs.forEachIndexed { i, ob ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(
                                "${if (i == 0) "★ " else "#${i + 1} "}${jsonGetString(ob, listOf("tag")).ifBlank { "(no tag)" }}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(jsonGetString(ob, listOf("protocol")), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.End) {
                            if (i > 0) TextButton(onClick = { val l = list().toMutableList(); l.add(i - 1, l.removeAt(i)); setList(l) }) { Text("↑") }
                            if (i < outs.size - 1) TextButton(onClick = { val l = list().toMutableList(); l.add(i + 1, l.removeAt(i)); setList(l) }) { Text("↓") }
                            if (onTestOutbound != null) TextButton(onClick = { testing = i }) { Text(tr("Test")) }
                            TextButton(onClick = { editing = i }) { Text(tr("Edit")) }
                            TextButton(onClick = { setList(list().filterIndexed { j, _ -> j != i }) }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("Add outbound")) }
            OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("Import from vless:// link")) }
        }
    }

    if (showAdd) {
        ProtocolPickerDialog(
            onPick = { proto -> showAdd = false; setList(list() + defaultOutbound(proto, proto + "-out")) },
            onDismiss = { showAdd = false },
        )
    }
    if (showImport) {
        ImportVlessDialog(
            onImport = { link ->
                val ob = parseVlessLink(link)
                showImport = false
                if (ob != null) setList(list() + ob)
            },
            onDismiss = { showImport = false },
        )
    }
    testing?.let { idx ->
        val ob = list().getOrNull(idx)
        if (ob == null || onTestOutbound == null) {
            testing = null
        } else {
            TestOutboundDialog(ob, onTestOutbound) { testing = null }
        }
    }
}

@Composable
private fun TestOutboundDialog(
    outboundJson: String,
    onTest: suspend (String, String) -> TestOutboundResult?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("tcp") }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<TestOutboundResult?>(null) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Test outbound")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                XrayChips(listOf("tcp", "http", "real"), mode) { mode = it }
                if (running) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(tr("Testing…"))
                    }
                }
                result?.let { r ->
                    if (r.success) {
                        Text("✓ ${r.delay} ms", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                        r.egress?.let { e ->
                            val ip = e.ipv4.ifBlank { e.ipv6 }
                            if (ip.isNotBlank()) {
                                val country = if (e.country.isNotBlank()) " · ${e.country}" else ""
                                val warp = if (e.warp.isNotBlank() && e.warp != "off") "  · WARP ${e.warp}" else ""
                                Text("$ip$country$warp", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Text("✗ ${r.error.ifBlank { tr("Failed") }}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (failed && result == null) {
                    Text(tr("Test request failed."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !running, onClick = {
                running = true; failed = false; result = null
                scope.launch {
                    val r = runCatching { onTest(outboundJson, mode) }.getOrNull()
                    result = r; failed = r == null; running = false
                }
            }) { Text(tr("Run test")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
    )
}

@Composable
private fun OutboundEditor(initial: String, saving: Boolean, onCancel: () -> Unit, onDone: (String) -> Unit) {
    var obj by remember { mutableStateOf(initial) }
    var raw by remember { mutableStateOf(false) }
    val protocol = jsonGetString(obj, listOf("protocol")).ifBlank { "freedom" }

    Column(Modifier.fillMaxSize()) {
        XrayEditHeader(tr("Outbound"), saving, onCancel, onSave = { onDone(obj) }, canSave = isValidJson(obj))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            XrayField(jsonGetString(obj, listOf("tag")), { obj = jsonPutString(obj, listOf("tag"), it) }, tr("Tag"))
            XrayLabel(tr("Protocol"))
            XrayChips(OUTBOUND_PROTOCOLS, protocol) { p -> obj = defaultOutbound(p, jsonGetString(obj, listOf("tag"))) }

            // Top-level target resolution (panel 3.5.0). Freedom/WireGuard carry
            // their own settings.domainStrategy control, so skip them here.
            if (protocol != "freedom" && protocol != "wireguard") {
                XrayLabel(tr("Target Strategy"))
                XrayChips(FREEDOM_DOMAIN_STRATEGY, jsonGetString(obj, listOf("targetStrategy")).ifBlank { "AsIs" }) { v ->
                    obj = if (v == "AsIs") jsonRemove(obj, listOf("targetStrategy"))
                    else jsonPutString(obj, listOf("targetStrategy"), v)
                }
            }

            if (protocol in FORMED_PROTOCOLS && !raw) {
                ProtocolForm(protocol, obj) { obj = it }
                if (protocol in PROXY_PROTOCOLS) {
                    XraySection(tr("Transport / security"))
                    TransportForm(obj) { obj = it }
                }
            }

            XrayToggle(tr("Edit raw JSON"), raw) { raw = it }
            if (raw || protocol !in FORMED_PROTOCOLS) {
                OutlinedTextField(
                    value = obj, onValueChange = { obj = it },
                    label = { Text(tr("Outbound (JSON)")) }, singleLine = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
                if (!isValidJson(obj)) Text(tr("Invalid JSON."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ProtocolForm(protocol: String, obj: String, onChange: (String) -> Unit) {
    fun g(vararg p: String) = jsonGetString(obj, p.toList())
    fun p(value: String, vararg path: String) = onChange(jsonPutString(obj, path.toList(), value))
    when (protocol) {
        "vless" -> {
            XrayField(g("settings", "address"), { p(it, "settings", "address") }, tr("Address"))
            XrayField(g("settings", "port"), { p(it.filter(Char::isDigit), "settings", "port") }, tr("Port"))
            XrayField(g("settings", "id"), { p(it, "settings", "id") }, tr("ID (UUID)"))
            XrayLabel(tr("Flow")); XrayChips(VLESS_FLOW, g("settings", "flow")) { p(it, "settings", "flow") }
        }
        "vmess" -> {
            XrayField(g("settings", "address"), { p(it, "settings", "address") }, tr("Address"))
            XrayField(g("settings", "port"), { p(it.filter(Char::isDigit), "settings", "port") }, tr("Port"))
            XrayField(g("settings", "id"), { p(it, "settings", "id") }, tr("ID (UUID)"))
            XrayLabel(tr("Security")); XrayChips(VMESS_SECURITY, g("settings", "security").ifBlank { "auto" }) { p(it, "settings", "security") }
        }
        "trojan" -> {
            XrayField(g("settings", "address"), { p(it, "settings", "address") }, tr("Address"))
            XrayField(g("settings", "port"), { p(it.filter(Char::isDigit), "settings", "port") }, tr("Port"))
            XrayField(g("settings", "password"), { p(it, "settings", "password") }, tr("Password"))
        }
        "shadowsocks" -> {
            XrayField(g("settings", "address"), { p(it, "settings", "address") }, tr("Address"))
            XrayField(g("settings", "port"), { p(it.filter(Char::isDigit), "settings", "port") }, tr("Port"))
            XrayLabel(tr("Method")); XrayChips(SS_METHODS, g("settings", "method").ifBlank { "aes-256-gcm" }) { p(it, "settings", "method") }
            XrayField(g("settings", "password"), { p(it, "settings", "password") }, tr("Password"))
        }
        "socks" -> {
            XrayField(g("settings", "address"), { p(it, "settings", "address") }, tr("Address"))
            XrayField(g("settings", "port"), { p(it.filter(Char::isDigit), "settings", "port") }, tr("Port"))
        }
        "http" -> {
            XrayField(g("settings", "address"), { p(it, "settings", "address") }, tr("Address"))
            XrayField(g("settings", "port"), { p(it.filter(Char::isDigit), "settings", "port") }, tr("Port"))
            // CONNECT headers sent to the upstream proxy (settings.headers, key→value).
            HttpHeadersEditor(jsonGetStringMap(obj, listOf("settings", "headers"))) { rows ->
                onChange(jsonPutStringMap(obj, listOf("settings", "headers"), rows))
            }
        }
        "freedom" -> {
            XrayLabel(tr("Domain strategy")); XrayChips(FREEDOM_DOMAIN_STRATEGY, g("settings", "domainStrategy").ifBlank { "AsIs" }) { p(it, "settings", "domainStrategy") }
        }
        "blackhole" -> {
            XrayLabel(tr("Response type")); XrayChips(BLACKHOLE_RESPONSE_TYPE, g("settings", "response", "type").ifBlank { "none" }) { p(it, "settings", "response", "type") }
        }
    }
}

/** key→value editor for an HTTP outbound's `settings.headers`. Keeps a local row
 *  list (so blank/duplicate keys while typing don't fight the map) and emits the
 *  rebuilt pairs on every edit; blank-key rows are dropped downstream. */
@Composable
private fun HttpHeadersEditor(initial: List<Pair<String, String>>, onChange: (List<Pair<String, String>>) -> Unit) {
    val rows = remember { mutableStateListOf<Pair<String, String>>().also { it.addAll(initial) } }
    fun emit() = onChange(rows.toList())
    XraySection(tr("Headers"))
    rows.forEachIndexed { i, (k, v) ->
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            OutlinedTextField(k, { rows[i] = it to v; emit() }, label = { Text(tr("Name")) }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(v, { rows[i] = k to it; emit() }, label = { Text(tr("Value")) }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { rows.removeAt(i); emit() }) { Text("✕", color = MaterialTheme.colorScheme.error) }
        }
    }
    OutlinedButton(onClick = { rows.add("" to "") }, modifier = Modifier.fillMaxWidth()) { Text("+ " + tr("Add header")) }
}

@Composable
private fun TransportForm(obj: String, onChange: (String) -> Unit) {
    fun g(vararg p: String) = jsonGetString(obj, p.toList())
    fun p(value: String, vararg path: String) = onChange(jsonPutString(obj, path.toList(), value))
    val network = g("streamSettings", "network").ifBlank { "tcp" }
    XrayLabel(tr("Network")); XrayChips(NETWORKS, network) { p(it, "streamSettings", "network") }
    when (network) {
        "ws" -> { XrayField(g("streamSettings", "wsSettings", "path"), { p(it, "streamSettings", "wsSettings", "path") }, tr("Path")); XrayField(g("streamSettings", "wsSettings", "host"), { p(it, "streamSettings", "wsSettings", "host") }, tr("Host")) }
        "grpc" -> XrayField(g("streamSettings", "grpcSettings", "serviceName"), { p(it, "streamSettings", "grpcSettings", "serviceName") }, tr("Service name"))
        "httpupgrade" -> { XrayField(g("streamSettings", "httpupgradeSettings", "path"), { p(it, "streamSettings", "httpupgradeSettings", "path") }, tr("Path")); XrayField(g("streamSettings", "httpupgradeSettings", "host"), { p(it, "streamSettings", "httpupgradeSettings", "host") }, tr("Host")) }
    }
    val security = g("streamSettings", "security").ifBlank { "none" }
    XrayLabel(tr("Security")); XrayChips(SECURITIES, security) { p(it, "streamSettings", "security") }
    when (security) {
        "tls" -> XrayField(g("streamSettings", "tlsSettings", "serverName"), { p(it, "streamSettings", "tlsSettings", "serverName") }, tr("SNI (server name)"))
        "reality" -> {
            XrayField(g("streamSettings", "realitySettings", "serverName"), { p(it, "streamSettings", "realitySettings", "serverName") }, tr("SNI (server name)"))
            XrayField(g("streamSettings", "realitySettings", "publicKey"), { p(it, "streamSettings", "realitySettings", "publicKey") }, tr("Public key"))
            XrayField(g("streamSettings", "realitySettings", "shortId"), { p(it, "streamSettings", "realitySettings", "shortId") }, tr("Short ID"))
        }
    }
}

/** Parse an outbounds import payload: a bare array, `{outbounds:[…]}`, or the
 *  wrapped form; each element is returned as a JSON-element string to append. */
private fun parseOutboundsImport(text: String): List<String>? {
    val el = try { Json.parseToJsonElement(text.trim()) } catch (e: Exception) { return null }
    val arr = when (el) {
        is JsonArray -> el
        is JsonObject -> el["outbounds"] as? JsonArray
        else -> null
    } ?: return null
    return arr.map { it.toString() }
}

@Composable
private fun ProtocolPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Add outbound")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OUTBOUND_PROTOCOLS.forEach { proto -> TextButton(onClick = { onPick(proto) }, modifier = Modifier.fillMaxWidth()) { Text(proto, Modifier.fillMaxWidth()) } }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun ImportVlessDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var link by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Import from vless:// link")) },
        text = { OutlinedTextField(link, { link = it }, label = { Text("vless://…") }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)) },
        confirmButton = { TextButton(onClick = { onImport(link) }, enabled = link.trim().startsWith("vless://")) { Text(tr("Import")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
