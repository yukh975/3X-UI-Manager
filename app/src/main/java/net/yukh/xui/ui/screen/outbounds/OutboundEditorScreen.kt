package net.yukh.xui.ui.screen.outbounds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.yukh.xui.data.api.dto.BLACKHOLE_RESPONSE_TYPE
import net.yukh.xui.data.api.dto.FREEDOM_DOMAIN_STRATEGY
import net.yukh.xui.data.api.dto.FREEDOM_FRAGMENT_PACKETS
import net.yukh.xui.data.api.dto.MUX_XUDP_443
import net.yukh.xui.data.api.dto.OUTBOUND_PROTOCOLS
import net.yukh.xui.data.api.dto.PROXY_PROTOCOLS
import net.yukh.xui.data.api.dto.SS_METHODS
import net.yukh.xui.data.api.dto.VLESS_FLOW
import net.yukh.xui.data.api.dto.VMESS_SECURITY
import net.yukh.xui.data.api.dto.WG_DOMAIN_STRATEGY
import net.yukh.xui.data.api.dto.defaultOutbound
import net.yukh.xui.data.api.dto.outboundProtocol
import net.yukh.xui.data.api.dto.outboundTag
import net.yukh.xui.data.json.array
import net.yukh.xui.data.json.asObject
import net.yukh.xui.data.json.bool
import net.yukh.xui.data.json.child
import net.yukh.xui.data.json.int
import net.yukh.xui.data.json.parseCsv
import net.yukh.xui.data.json.put
import net.yukh.xui.data.json.putArray
import net.yukh.xui.data.json.putBool
import net.yukh.xui.data.json.putInt
import net.yukh.xui.data.json.putString
import net.yukh.xui.data.json.putStrings
import net.yukh.xui.data.json.string
import net.yukh.xui.data.json.strings
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.Field
import net.yukh.xui.ui.components.LabeledDropdown
import net.yukh.xui.ui.components.OutboundStreamSettings
import net.yukh.xui.ui.components.SectionTitle
import net.yukh.xui.ui.components.SwitchRow

private val editorJson = Json { prettyPrint = true; isLenient = true }

private fun pretty(o: JsonObject): String =
    editorJson.encodeToString(JsonElement.serializer(), o)

private fun JsonObject.settings(): JsonObject = child("settings")
private fun JsonObject.putSettings(s: JsonObject): JsonObject = put("settings", s)

@Composable
fun OutboundEditorScreen(
    editing: EditingOutbound,
    error: String?,
    onDraftChange: (JsonObject) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val draft = editing.draft
    val protocol = draft.outboundProtocol()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing.isNew) tr("New outbound") else tr("Edit outbound")) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close"))
                    }
                },
                actions = { TextButton(onClick = onDone) { Text(tr("Done")) } },
            )
        },
        bottomBar = {
            if (onDelete != null) {
                Surface(tonalElevation = 2.dp) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Text("  " + tr("Delete"))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Field(tr("Tag"), draft.outboundTag()) { onDraftChange(draft.putString("tag", it)) }
            LabeledDropdown(tr("Protocol"), protocol, OUTBOUND_PROTOCOLS) { np ->
                if (np != protocol) onDraftChange(defaultOutbound(np, draft.outboundTag()))
            }
            Field(tr("Send through"), draft.string("sendThrough")) { onDraftChange(draft.putString("sendThrough", it)) }

            // Top-level target resolution (panel 3.5.0). Freedom/WireGuard carry
            // their own settings.domainStrategy control, so skip them here.
            if (protocol != "freedom" && protocol != "wireguard") {
                LabeledDropdown(
                    tr("Target Strategy"),
                    draft.string("targetStrategy").ifBlank { "AsIs" },
                    FREEDOM_DOMAIN_STRATEGY,
                ) { v ->
                    onDraftChange(
                        if (v == "AsIs") draft.put("targetStrategy", null)
                        else draft.putString("targetStrategy", v),
                    )
                }
            }

            when (protocol) {
                "freedom" -> FreedomForm(draft, onDraftChange)
                "blackhole" -> BlackholeForm(draft, onDraftChange)
                "socks", "http" -> ServerAuthForm(draft, protocol, onDraftChange)
                "vmess" -> VmessForm(draft, onDraftChange)
                "vless" -> VlessForm(draft, onDraftChange)
                "trojan" -> TrojanForm(draft, onDraftChange)
                "shadowsocks" -> ShadowsocksForm(draft, onDraftChange)
                "wireguard" -> WireguardForm(draft, onDraftChange)
                else -> RawSettingsForm(draft, protocol, onDraftChange)
            }

            if (protocol in PROXY_PROTOCOLS) {
                OutboundStreamSettings(draft, onDraftChange)
                MuxSection(draft, onDraftChange)
            }
        }
    }
}

@Composable
private fun FreedomForm(draft: JsonObject, onChange: (JsonObject) -> Unit) {
    val s = draft.settings()
    SectionTitle(tr("Freedom"))
    LabeledDropdown(tr("Strategy"), s.string("domainStrategy").ifBlank { "AsIs" }, FREEDOM_DOMAIN_STRATEGY) {
        onChange(draft.putSettings(s.putString("domainStrategy", it)))
    }
    Field(tr("Redirect"), s.string("redirect")) { onChange(draft.putSettings(s.putString("redirect", it))) }

    val frag = s["fragment"] as? JsonObject
    SwitchRow(tr("Fragment"), frag != null) { on ->
        val newFrag = if (on) {
            JsonObject(emptyMap()).putString("packets", "tlshello").putString("length", "100-200").putString("interval", "10-20")
        } else null
        onChange(draft.putSettings(s.put("fragment", newFrag)))
    }
    if (frag != null) {
        LabeledDropdown(tr("Packets"), frag.string("packets").ifBlank { "tlshello" }, FREEDOM_FRAGMENT_PACKETS) {
            onChange(draft.putSettings(s.put("fragment", frag.putString("packets", it))))
        }
        Field(tr("Length"), frag.string("length")) { onChange(draft.putSettings(s.put("fragment", frag.putString("length", it)))) }
        Field(tr("Interval"), frag.string("interval")) { onChange(draft.putSettings(s.put("fragment", frag.putString("interval", it)))) }
        Field(tr("Max Split"), frag.string("maxSplit")) { onChange(draft.putSettings(s.put("fragment", frag.putString("maxSplit", it)))) }
    }
}

@Composable
private fun BlackholeForm(draft: JsonObject, onChange: (JsonObject) -> Unit) {
    val s = draft.settings()
    val resp = s.child("response")
    SectionTitle(tr("Blackhole"))
    LabeledDropdown(
        tr("Response type"),
        resp.string("type").ifBlank { "none" },
        BLACKHOLE_RESPONSE_TYPE,
    ) { onChange(draft.putSettings(s.put("response", resp.putString("type", it)))) }
}

@Composable
private fun ServerAuthForm(draft: JsonObject, protocol: String, onChange: (JsonObject) -> Unit) {
    val s = draft.settings()
    SectionTitle(tr("Server"))
    Field(tr("Address"), s.string("address")) { onChange(draft.putSettings(s.putString("address", it))) }
    Field(tr("Port"), s.int("port")?.toString() ?: "", numeric = true) {
        onChange(draft.putSettings(s.putInt("port", it.toIntOrNull() ?: 0)))
    }
    Field(tr("Username (optional)"), s.string("user")) { onChange(draft.putSettings(s.putString("user", it))) }
    Field(tr("Password (optional)"), s.string("pass")) { onChange(draft.putSettings(s.putString("pass", it))) }

    // HTTP CONNECT headers sent to the upstream proxy (settings.headers, key→value).
    if (protocol == "http") {
        HttpHeadersEditor(s.child("headers")) { h ->
            onChange(draft.putSettings(if (h.isEmpty()) s.put("headers", null) else s.put("headers", h)))
        }
    }
}

/** key→value editor for an HTTP outbound's `settings.headers`. Keeps a local row
 *  list (so blank/duplicate keys while typing don't fight the map) and emits the
 *  rebuilt object on every edit; blank-key rows are dropped. */
@Composable
private fun HttpHeadersEditor(headers: JsonObject, onChange: (JsonObject) -> Unit) {
    val rows = remember {
        mutableStateListOf<Pair<String, String>>().also { list ->
            headers.entries.forEach { (k, v) -> list.add(k to ((v as? JsonPrimitive)?.content ?: "")) }
        }
    }
    fun emit() {
        onChange(JsonObject(rows.filter { it.first.isNotBlank() }.associate { it.first to JsonPrimitive(it.second) }))
    }
    SectionTitle(tr("Headers"))
    rows.forEachIndexed { i, (k, v) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = k, onValueChange = { rows[i] = it to v; emit() },
                label = { Text(tr("Name")) }, singleLine = true, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = v, onValueChange = { rows[i] = k to it; emit() },
                label = { Text(tr("Value")) }, singleLine = true, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { rows.removeAt(i); emit() }) {
                Icon(Icons.Outlined.Delete, contentDescription = tr("Delete"))
            }
        }
    }
    OutlinedButton(onClick = { rows.add("" to "") }) {
        Icon(Icons.Filled.Add, contentDescription = null); Text("  " + tr("Add header"))
    }
}

@Composable
private fun VmessForm(draft: JsonObject, onChange: (JsonObject) -> Unit) {
    val s = draft.settings()
    SectionTitle(tr("Server"))
    Field(tr("Address"), s.string("address")) { onChange(draft.putSettings(s.putString("address", it))) }
    Field(tr("Port"), s.int("port")?.toString() ?: "", numeric = true) { onChange(draft.putSettings(s.putInt("port", it.toIntOrNull() ?: 0))) }
    Field(tr("ID (UUID)"), s.string("id")) { onChange(draft.putSettings(s.putString("id", it))) }
    LabeledDropdown(tr("Encryption"), s.string("security").ifBlank { "auto" }, VMESS_SECURITY) {
        onChange(draft.putSettings(s.putString("security", it)))
    }
}

@Composable
private fun VlessForm(draft: JsonObject, onChange: (JsonObject) -> Unit) {
    val s = draft.settings()
    SectionTitle(tr("Server"))
    Field(tr("Address"), s.string("address")) { onChange(draft.putSettings(s.putString("address", it))) }
    Field(tr("Port"), s.int("port")?.toString() ?: "", numeric = true) { onChange(draft.putSettings(s.putInt("port", it.toIntOrNull() ?: 0))) }
    Field(tr("ID (UUID)"), s.string("id")) { onChange(draft.putSettings(s.putString("id", it))) }
    Field(tr("Encryption"), s.string("encryption").ifBlank { "none" }) { onChange(draft.putSettings(s.putString("encryption", it))) }
    LabeledDropdown(tr("Flow"), s.string("flow"), VLESS_FLOW) { onChange(draft.putSettings(s.putString("flow", it))) }
}

@Composable
private fun TrojanForm(draft: JsonObject, onChange: (JsonObject) -> Unit) {
    val s = draft.settings()
    SectionTitle(tr("Server"))
    Field(tr("Address"), s.string("address")) { onChange(draft.putSettings(s.putString("address", it))) }
    Field(tr("Port"), s.int("port")?.toString() ?: "", numeric = true) { onChange(draft.putSettings(s.putInt("port", it.toIntOrNull() ?: 0))) }
    Field(tr("Password"), s.string("password")) { onChange(draft.putSettings(s.putString("password", it))) }
}

@Composable
private fun ShadowsocksForm(draft: JsonObject, onChange: (JsonObject) -> Unit) {
    val s = draft.settings()
    SectionTitle(tr("Server"))
    Field(tr("Address"), s.string("address")) { onChange(draft.putSettings(s.putString("address", it))) }
    Field(tr("Port"), s.int("port")?.toString() ?: "", numeric = true) { onChange(draft.putSettings(s.putInt("port", it.toIntOrNull() ?: 0))) }
    LabeledDropdown(tr("Method"), s.string("method").ifBlank { "aes-256-gcm" }, SS_METHODS) { onChange(draft.putSettings(s.putString("method", it))) }
    Field(tr("Password"), s.string("password")) { onChange(draft.putSettings(s.putString("password", it))) }
    SwitchRow(tr("UDP over TCP (uot)"), s.bool("uot")) { onChange(draft.putSettings(s.putBool("uot", it))) }
    Field(tr("UoT version"), s.int("UoTVersion")?.toString() ?: "", numeric = true) {
        onChange(draft.putSettings(s.putInt("UoTVersion", it.toIntOrNull() ?: 1)))
    }
}

@Composable
private fun WireguardForm(draft: JsonObject, onChange: (JsonObject) -> Unit) {
    val s = draft.settings()
    SectionTitle(tr("Interface"))
    Field(tr("Address (CIDR, comma-separated)"), s.strings("address").joinToString(", ")) {
        onChange(draft.putSettings(s.putStrings("address", parseCsv(it))))
    }
    Field(tr("Secret key"), s.string("secretKey")) { onChange(draft.putSettings(s.putString("secretKey", it))) }
    Field(tr("Public key"), s.string("pubKey")) { onChange(draft.putSettings(s.putString("pubKey", it))) }
    LabeledDropdown(tr("Domain strategy"), s.string("domainStrategy"), WG_DOMAIN_STRATEGY) {
        onChange(draft.putSettings(s.putString("domainStrategy", it)))
    }
    Field(tr("MTU"), s.int("mtu")?.toString() ?: "", numeric = true) {
        onChange(draft.putSettings(s.putInt("mtu", it.toIntOrNull() ?: 1420)))
    }
    // `workers` was dropped in xray-core v26.6.22 (panel 3.4.0) — field removed.
    SwitchRow(tr("No-kernel TUN"), s.bool("noKernelTun")) { onChange(draft.putSettings(s.putBool("noKernelTun", it))) }
    Field(tr("Reserved"), s.string("reserved")) { onChange(draft.putSettings(s.putString("reserved", it))) }

    val p = (s["peers"] as? JsonArray)?.firstOrNull()?.asObject() ?: JsonObject(emptyMap())
    fun writePeer(np: JsonObject) {
        val arr = s.array("peers").toMutableList()
        if (arr.isEmpty()) arr.add(np) else arr[0] = np
        onChange(draft.putSettings(s.putArray("peers", arr)))
    }
    SectionTitle(tr("Peer"))
    Field(tr("Endpoint"), p.string("endpoint")) { writePeer(p.putString("endpoint", it)) }
    Field(tr("Public key"), p.string("publicKey")) { writePeer(p.putString("publicKey", it)) }
    Field(tr("PSK"), p.string("psk")) { writePeer(p.putString("psk", it)) }
    Field(tr("Allowed IPs (comma-separated)"), p.strings("allowedIPs").joinToString(", ")) {
        writePeer(p.putStrings("allowedIPs", parseCsv(it)))
    }
    Field(tr("Keep alive"), p.int("keepAlive")?.toString() ?: "", numeric = true) {
        writePeer(p.putInt("keepAlive", it.toIntOrNull() ?: 0))
    }
}

@Composable
private fun MuxSection(draft: JsonObject, onChange: (JsonObject) -> Unit) {
    val mux = draft.child("mux")
    val enabled = mux.bool("enabled")
    SectionTitle(tr("Mux"))
    SwitchRow(tr("Mux"), enabled) { onChange(draft.put("mux", mux.putBool("enabled", it))) }
    if (enabled) {
        Field(tr("Concurrency"), mux.int("concurrency")?.toString() ?: "", numeric = true) {
            onChange(draft.put("mux", mux.putInt("concurrency", it.toIntOrNull() ?: 8)))
        }
        Field(tr("xudp concurrency"), mux.int("xudpConcurrency")?.toString() ?: "", numeric = true) {
            onChange(draft.put("mux", mux.putInt("xudpConcurrency", it.toIntOrNull() ?: 16)))
        }
        LabeledDropdown(tr("xudp UDP 443"), mux.string("xudpProxyUDP443").ifBlank { "reject" }, MUX_XUDP_443) {
            onChange(draft.put("mux", mux.putString("xudpProxyUDP443", it)))
        }
    }
}

@Composable
private fun RawSettingsForm(draft: JsonObject, protocol: String, onChange: (JsonObject) -> Unit) {
    val invalidJson = tr("Invalid JSON")
    SectionTitle(tr("Settings (raw JSON)"))
    Text(
        tr("A structured form for this protocol is coming; edit the raw JSON for now."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    var settingsText by remember(protocol) { mutableStateOf(pretty(draft.settings())) }
    var settingsErr by remember(protocol) { mutableStateOf<String?>(null) }
    OutlinedTextField(
        value = settingsText,
        onValueChange = { txt ->
            settingsText = txt
            runCatching { editorJson.parseToJsonElement(txt).asObject() }
                .onSuccess { settingsErr = null; onChange(draft.put("settings", it)) }
                .onFailure { settingsErr = invalidJson }
        },
        label = { Text("settings") },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
    )
    settingsErr?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

    if (protocol in PROXY_PROTOCOLS) {
        var streamText by remember(protocol) { mutableStateOf(pretty(draft.child("streamSettings"))) }
        var streamErr by remember(protocol) { mutableStateOf<String?>(null) }
        OutlinedTextField(
            value = streamText,
            onValueChange = { txt ->
                streamText = txt
                runCatching { editorJson.parseToJsonElement(txt).asObject() }
                    .onSuccess { streamErr = null; onChange(draft.put("streamSettings", it)) }
                    .onFailure { streamErr = invalidJson }
            },
            label = { Text("streamSettings") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
        )
        streamErr?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
