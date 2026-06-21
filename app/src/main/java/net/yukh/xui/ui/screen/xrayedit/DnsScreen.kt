package net.yukh.xui.ui.screen.xrayedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import net.yukh.xui.ui.components.ConfirmDialog
import net.yukh.xui.ui.components.Field
import net.yukh.xui.ui.components.LabeledDropdown
import net.yukh.xui.ui.components.SectionTitle
import net.yukh.xui.ui.components.SwitchRow

private val DNS_QUERY_STRATEGY = listOf("UseIP", "UseIPv4", "UseIPv6", "UseSystem")

private fun serverToObject(el: kotlinx.serialization.json.JsonElement): JsonObject = when (el) {
    is JsonObject -> el
    is JsonPrimitive -> JsonObject(mapOf("address" to el))
    else -> JsonObject(emptyMap())
}

/** Collapse a server object back to a bare string when only the address is set. */
private fun serverToElement(o: JsonObject): kotlinx.serialization.json.JsonElement {
    val plain = o.strings("domains").isEmpty() && o.strings("expectIPs").isEmpty() &&
        o.strings("unexpectedIPs").isEmpty() && (o.int("port") == null || o.int("port") == 53) &&
        (o.string("queryStrategy").isBlank() || o.string("queryStrategy") == "UseIP") &&
        !o.bool("disableCache") && !o.bool("finalQuery")
    return if (plain) JsonPrimitive(o.string("address")) else o
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsScreen(onClose: () -> Unit, vm: DnsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.savedMessage) { state.savedMessage?.let { snackbar.showSnackbar(it); vm.dismissSavedMessage() } }
    LaunchedEffect(state.error) { if (state.available) state.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }

    val editing = state.editingServer
    if (editing != null) {
        DnsServerEditor(
            draft = editing.draft,
            isNew = editing.isNew,
            onChange = vm::updateServerDraft,
            onCancel = vm::closeServer,
            onDone = {
                val cfg = state.config
                val dns = cfg.child("dns")
                val arr = dns.array("servers").toMutableList()
                val el = serverToElement(editing.draft)
                if (editing.isNew) arr.add(el) else arr[editing.index] = el
                vm.update(cfg.put("dns", dns.putArray("servers", arr)))
                vm.closeServer()
            },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(tr("DNS")) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close")) } },
                actions = {
                    if (state.available) TextButton(onClick = vm::save, enabled = state.dirty && !state.saving) {
                        if (state.saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text(tr("Save"))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar { Text(it.visuals.message) } } },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                !state.available -> SessionGate()
                else -> DnsBody(state.config, vm)
            }
        }
    }
}

@Composable
private fun DnsBody(cfg: JsonObject, vm: DnsViewModel) {
    val dnsEnabled = cfg["dns"] is JsonObject
    val dns = cfg.child("dns")
    var pendingDelete by remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SwitchRow(tr("Enable DNS"), dnsEnabled) { on ->
            if (on) {
                val def = JsonObject(emptyMap()).putString("tag", "dns_inbound").putString("queryStrategy", "UseIP")
                    .putArray("servers", emptyList())
                vm.update(cfg.put("dns", def))
            } else {
                vm.update(cfg.put("dns", null).put("fakedns", null))
            }
        }

        if (dnsEnabled) {
            fun setDns(d: JsonObject) = vm.update(cfg.put("dns", d))
            Field(tr("DNS tag"), dns.string("tag")) { setDns(dns.putString("tag", it)) }
            Field(tr("Client IP"), dns.string("clientIp")) { setDns(dns.putString("clientIp", it)) }
            LabeledDropdown(tr("Query strategy"), dns.string("queryStrategy").ifBlank { "UseIP" }, DNS_QUERY_STRATEGY) {
                setDns(dns.putString("queryStrategy", it))
            }
            SwitchRow(tr("Disable cache"), dns.bool("disableCache")) { setDns(dns.putBool("disableCache", it)) }
            SwitchRow(tr("Disable fallback"), dns.bool("disableFallback")) { setDns(dns.putBool("disableFallback", it)) }
            SwitchRow(tr("Disable fallback if match"), dns.bool("disableFallbackIfMatch")) { setDns(dns.putBool("disableFallbackIfMatch", it)) }
            SwitchRow(tr("Parallel query"), dns.bool("enableParallelQuery")) { setDns(dns.putBool("enableParallelQuery", it)) }
            SwitchRow(tr("Use system hosts"), dns.bool("useSystemHosts")) { setDns(dns.putBool("useSystemHosts", it)) }

            SectionTitle(tr("DNS servers"))
            val servers = dns.array("servers")
            servers.forEachIndexed { i, el ->
                val o = serverToObject(el)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { vm.openServer(i, o, isNew = false) },
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(o.string("address").ifBlank { "(no address)" }, style = MaterialTheme.typography.titleSmall)
                            val sub = o.strings("domains").joinToString(", ").ifBlank { o.strings("expectIPs").joinToString(", ") }
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { pendingDelete = i }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Delete")) }
                    }
                }
            }
            OutlinedButton(onClick = { vm.openServer(-1, JsonObject(emptyMap()).putString("address", ""), isNew = true) }) {
                Icon(Icons.Filled.Add, contentDescription = null); Text("  " + tr("Add DNS server"))
            }

            SectionTitle(tr("FakeDNS"))
            val fake = (cfg["fakedns"] as? JsonArray) ?: JsonArray(emptyList())
            fun setFake(items: List<kotlinx.serialization.json.JsonElement>) =
                vm.update(if (items.isEmpty()) cfg.put("fakedns", null) else cfg.putArray("fakedns", items))
            fake.forEachIndexed { i, el ->
                val o = el.asObject()
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        Field(tr("IP pool"), o.string("ipPool")) {
                            val arr = fake.toMutableList(); arr[i] = o.putString("ipPool", it); setFake(arr)
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        Field(tr("Pool size"), o.int("poolSize")?.toString() ?: "", numeric = true) {
                            val arr = fake.toMutableList(); arr[i] = o.putInt("poolSize", it.toIntOrNull() ?: 65535); setFake(arr)
                        }
                    }
                    IconButton(onClick = { val arr = fake.toMutableList(); arr.removeAt(i); setFake(arr) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = tr("Delete"))
                    }
                }
            }
            OutlinedButton(onClick = {
                val def = JsonObject(emptyMap()).putString("ipPool", "198.18.0.0/15").putInt("poolSize", 65535)
                setFake(fake.toMutableList().apply { add(def) })
            }) { Icon(Icons.Filled.Add, contentDescription = null); Text("  " + tr("Add FakeDNS")) }
        }
    }

    pendingDelete?.let { idx ->
        ConfirmDialog(
            title = tr("Delete DNS server?"),
            text = serverToObject(cfg.child("dns").array("servers").getOrElse(idx) { JsonPrimitive("") }).string("address"),
            confirmLabel = tr("Delete"),
            destructive = true,
            onConfirm = {
                pendingDelete = null
                val dns = cfg.child("dns")
                val arr = dns.array("servers").toMutableList(); if (idx in arr.indices) arr.removeAt(idx)
                vm.update(cfg.put("dns", dns.putArray("servers", arr)))
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsServerEditor(
    draft: JsonObject,
    isNew: Boolean,
    onChange: (JsonObject) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) tr("New DNS server") else tr("Edit DNS server")) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close")) } },
                actions = { TextButton(onClick = onDone) { Text(tr("Done")) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field(tr("Address"), draft.string("address")) { onChange(draft.putString("address", it)) }
            Field(tr("Port"), draft.int("port")?.toString() ?: "", numeric = true) { onChange(draft.putInt("port", it.toIntOrNull() ?: 53)) }
            LabeledDropdown(tr("Query strategy"), draft.string("queryStrategy").ifBlank { "UseIP" }, DNS_QUERY_STRATEGY) {
                onChange(draft.putString("queryStrategy", it))
            }
            Field(tr("Domains (comma-separated)"), draft.strings("domains").joinToString(", ")) { onChange(draft.putStrings("domains", parseCsv(it))) }
            Field(tr("Expected IPs (comma-separated)"), draft.strings("expectIPs").joinToString(", ")) { onChange(draft.putStrings("expectIPs", parseCsv(it))) }
            Field(tr("Unexpected IPs (comma-separated)"), draft.strings("unexpectedIPs").joinToString(", ")) { onChange(draft.putStrings("unexpectedIPs", parseCsv(it))) }
            SwitchRow(tr("Skip fallback"), if (draft["skipFallback"] == null) true else draft.bool("skipFallback")) { onChange(draft.putBool("skipFallback", it)) }
            SwitchRow(tr("Disable cache"), draft.bool("disableCache")) { onChange(draft.putBool("disableCache", it)) }
            SwitchRow(tr("Final query"), draft.bool("finalQuery")) { onChange(draft.putBool("finalQuery", it)) }
        }
    }
}
