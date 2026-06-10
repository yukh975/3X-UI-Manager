package net.yukh.xui.ui.screen.outbounds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.data.api.dto.BLACKHOLE_RESPONSE_TYPE
import net.yukh.xui.data.api.dto.FREEDOM_DOMAIN_STRATEGY
import net.yukh.xui.data.api.dto.OUTBOUND_PROTOCOLS
import net.yukh.xui.data.api.dto.PROXY_PROTOCOLS
import net.yukh.xui.data.api.dto.defaultOutbound
import net.yukh.xui.data.api.dto.outboundProtocol
import net.yukh.xui.data.api.dto.outboundTag
import net.yukh.xui.data.json.array
import net.yukh.xui.data.json.asObject
import net.yukh.xui.data.json.child
import net.yukh.xui.data.json.int
import net.yukh.xui.data.json.put
import net.yukh.xui.data.json.putArray
import net.yukh.xui.data.json.putInt
import net.yukh.xui.data.json.putString
import net.yukh.xui.data.json.string
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.Field
import net.yukh.xui.ui.components.LabeledDropdown
import net.yukh.xui.ui.components.SectionTitle

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

            when (protocol) {
                "freedom" -> FreedomForm(draft, onDraftChange)
                "blackhole" -> BlackholeForm(draft, onDraftChange)
                "socks", "http" -> ServerAuthForm(draft, onDraftChange)
                else -> RawSettingsForm(draft, protocol, onDraftChange)
            }
        }
    }
}

@Composable
private fun FreedomForm(draft: JsonObject, onChange: (JsonObject) -> Unit) {
    val s = draft.settings()
    SectionTitle(tr("Freedom"))
    LabeledDropdown(
        tr("Domain strategy"),
        s.string("domainStrategy").ifBlank { "AsIs" },
        FREEDOM_DOMAIN_STRATEGY,
    ) { onChange(draft.putSettings(s.putString("domainStrategy", it))) }
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
private fun ServerAuthForm(draft: JsonObject, onChange: (JsonObject) -> Unit) {
    val s = draft.settings()
    val srv = (s["servers"] as? JsonArray)?.firstOrNull()?.asObject() ?: JsonObject(emptyMap())

    fun writeServer(newSrv: JsonObject) {
        val arr = s.array("servers").toMutableList()
        if (arr.isEmpty()) arr.add(newSrv) else arr[0] = newSrv
        onChange(draft.putSettings(s.putArray("servers", arr)))
    }

    val user0 = (srv["users"] as? JsonArray)?.firstOrNull()?.asObject() ?: JsonObject(emptyMap())
    fun writeUser(u: JsonObject) {
        val hasAuth = u.string("user").isNotBlank() || u.string("pass").isNotBlank()
        writeServer(if (hasAuth) srv.putArray("users", listOf(u)) else srv.put("users", null))
    }

    SectionTitle(tr("Server"))
    Field(tr("Address"), srv.string("address")) { writeServer(srv.putString("address", it)) }
    Field(tr("Port"), srv.int("port")?.toString() ?: "", numeric = true) {
        writeServer(srv.putInt("port", it.toIntOrNull() ?: 0))
    }
    Field(tr("Username (optional)"), user0.string("user")) { writeUser(user0.putString("user", it)) }
    Field(tr("Password (optional)"), user0.string("pass")) { writeUser(user0.putString("pass", it)) }
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
