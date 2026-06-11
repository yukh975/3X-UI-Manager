package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.yukh.xui.shared.json.jsonGetBool
import net.yukh.xui.shared.json.jsonGetChild
import net.yukh.xui.shared.json.jsonGetObjectList
import net.yukh.xui.shared.json.jsonGetString
import net.yukh.xui.shared.json.jsonGetStrings
import net.yukh.xui.shared.json.jsonIsObject
import net.yukh.xui.shared.json.jsonPutBool
import net.yukh.xui.shared.json.jsonPutString
import net.yukh.xui.shared.json.jsonPutStrings
import net.yukh.xui.shared.json.jsonRemove
import net.yukh.xui.shared.json.jsonSetObjectList
import net.yukh.xui.shared.json.parseCsvList

private val QUERY_STRATEGY = listOf("UseIP", "UseIPv4", "UseIPv6", "UseSystem")
private val DNS_TOGGLES = listOf(
    "disableCache" to "Disable cache",
    "disableFallback" to "Disable fallback",
    "disableFallbackIfMatch" to "Disable fallback if match",
    "useSystemHosts" to "Use system hosts",
)

/** Structured DNS section of the Xray config. DNS is off when there's no `dns`
 *  key; turning it on seeds a minimal object. Servers may be a bare address
 *  string or a full object — edited servers round-trip as objects. */
@Composable
fun DnsXrayScreen(
    configJson: String,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    onConfigChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val enabled = jsonIsObject(jsonGetChild(configJson, listOf("dns")))
    var editServer by remember { mutableStateOf<Int?>(null) } // index, -1 = new

    fun dnsStr(key: String) = jsonGetString(configJson, listOf("dns", key))
    fun putDnsStr(key: String, v: String) = onConfigChange(jsonPutString(configJson, listOf("dns", key), v))
    fun servers(): List<String> = jsonGetObjectList(configJson, listOf("dns", "servers"))
    fun setServers(list: List<String>) = onConfigChange(jsonSetObjectList(configJson, listOf("dns", "servers"), list))

    Column(Modifier.fillMaxSize()) {
        XrayEditHeader(tr("DNS"), saving, onCancel, onSave, canSave = !loading)
        if (error != null) Text(error, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            XrayToggle(tr("Enable DNS"), enabled) { on ->
                onConfigChange(
                    if (on) jsonPutString(configJson, listOf("dns", "queryStrategy"), "UseIP")
                    else jsonRemove(configJson, listOf("dns")),
                )
            }
            if (!enabled) return@Column

            XrayField(dnsStr("tag"), { putDnsStr("tag", it) }, tr("Tag"))
            XrayField(dnsStr("clientIp"), { putDnsStr("clientIp", it) }, tr("Client IP"))
            XrayLabel(tr("Query strategy"))
            XrayChips(QUERY_STRATEGY, dnsStr("queryStrategy").ifBlank { "UseIP" }) { putDnsStr("queryStrategy", it) }
            DNS_TOGGLES.forEach { (key, label) ->
                XrayToggle(tr(label), jsonGetBool(configJson, listOf("dns", key))) {
                    onConfigChange(jsonPutBool(configJson, listOf("dns", key), it))
                }
            }

            XraySection(tr("DNS servers"))
            servers().forEachIndexed { i, srv ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(serverAddress(srv).ifBlank { "—" }, Modifier.weight(1f))
                        TextButton(onClick = { editServer = i }) { Text(tr("Edit")) }
                        TextButton(onClick = { setServers(servers().filterIndexed { j, _ -> j != i }) }) {
                            Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            OutlinedButton(onClick = { editServer = -1 }, modifier = Modifier.fillMaxWidth()) { Text(tr("Add DNS server")) }
        }
    }

    editServer?.let { idx ->
        val initial = if (idx >= 0) servers().getOrElse(idx) { "{}" } else "{}"
        DnsServerDialog(
            initial = initial,
            onConfirm = { obj ->
                val list = servers().toMutableList()
                if (idx >= 0) list[idx] = obj else list.add(obj)
                setServers(list)
                editServer = null
            },
            onDismiss = { editServer = null },
        )
    }
}

/** Edit one DNS server (always written as an object). */
@Composable
private fun DnsServerDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    // Seed from an object, or wrap a bare address string into { address }.
    val seed = if (jsonIsObject(initial)) initial else jsonPutString("{}", listOf("address"), serverAddress(initial))
    var obj by remember { mutableStateOf(seed) }
    fun g(k: String) = jsonGetString(obj, listOf(k))
    fun p(k: String, v: String) { obj = jsonPutString(obj, listOf(k), v) }
    fun csv(k: String) = jsonGetStrings(obj, listOf(k)).joinToString(", ")
    fun pcsv(k: String, v: String) { obj = jsonPutStrings(obj, listOf(k), parseCsvList(v)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("DNS server")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                XrayField(g("address"), { p("address", it) }, tr("Address"))
                XrayField(g("port"), { p("port", it.filter(Char::isDigit)) }, tr("Port (blank = 53)"))
                XrayLabel(tr("Query strategy"))
                XrayChips(QUERY_STRATEGY, g("queryStrategy").ifBlank { "UseIP" }) { p("queryStrategy", it) }
                XrayField(csv("domains"), { pcsv("domains", it) }, tr("Domains (comma-separated)"))
                XrayField(csv("expectIPs"), { pcsv("expectIPs", it) }, tr("Expected IPs (comma-separated)"))
                XrayToggle(tr("Skip fallback"), jsonGetBool(obj, listOf("skipFallback"))) { obj = jsonPutBool(obj, listOf("skipFallback"), it) }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(obj) }, enabled = g("address").isNotBlank()) { Text(tr("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** A DNS server's address — its `address` field if it's an object, else the bare
 *  address string itself (servers may be either form). */
private fun serverAddress(srv: String): String =
    if (jsonIsObject(srv)) jsonGetString(srv, listOf("address")) else srv.trim().removeSurrounding("\"")
