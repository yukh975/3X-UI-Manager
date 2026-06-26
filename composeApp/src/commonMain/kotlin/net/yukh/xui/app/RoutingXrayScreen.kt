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
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.shared.json.jsonGetObjectList
import net.yukh.xui.shared.json.jsonGetString
import net.yukh.xui.shared.json.jsonGetStrings
import net.yukh.xui.shared.json.jsonPutBool
import net.yukh.xui.shared.json.jsonPutString
import net.yukh.xui.shared.json.jsonPutStrings
import net.yukh.xui.shared.json.jsonRemove
import net.yukh.xui.shared.json.jsonSetObjectList
import net.yukh.xui.shared.json.parseCsvList

private val ROUTING_STRATEGY = listOf("AsIs", "IPIfNonMatch", "IPOnDemand")
private val NETWORKS = listOf("", "tcp", "udp", "tcp,udp")
private val BALANCER_STRATEGY = listOf("random", "roundRobin", "leastLoad", "leastPing")
private val RULE_CSV = listOf(
    "inboundTag" to "Inbound tags",
    "domain" to "Domains",
    "ip" to "IPs",
    "source" to "Source IPs",
    "user" to "Users",
    "protocol" to "Protocols",
)

/** Structured Routing section: domainStrategy + rules + balancers, all edited on
 *  the one config JSON string. Rules/balancers are object arrays. */
@Composable
fun RoutingXrayScreen(
    configJson: String,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    onConfigChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var editRule by remember { mutableStateOf<Int?>(null) }
    var editBal by remember { mutableStateOf<Int?>(null) }

    fun rules() = jsonGetObjectList(configJson, listOf("routing", "rules"))
    fun setRules(l: List<String>) = onConfigChange(jsonSetObjectList(configJson, listOf("routing", "rules"), l))
    fun bals() = jsonGetObjectList(configJson, listOf("routing", "balancers"))
    fun setBals(l: List<String>) = onConfigChange(jsonSetObjectList(configJson, listOf("routing", "balancers"), l))

    Column(Modifier.fillMaxSize()) {
        XrayEditHeader(tr("Routing"), saving, onCancel, onSave, canSave = !loading)
        if (error != null) Text(error, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            XrayLabel(tr("Routing strategy"))
            XrayChips(ROUTING_STRATEGY, jsonGetString(configJson, listOf("routing", "domainStrategy")).ifBlank { "AsIs" }) {
                onConfigChange(jsonPutString(configJson, listOf("routing", "domainStrategy"), it))
            }

            // Compact icon buttons (⬆ import / ⬇ export) with the heading taking the
            // flex space — full words overflowed the row and wrapped one letter per
            // line on narrow screens.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { XraySection(tr("Routing rules")) }
                TextButton(onClick = {
                    platformPickFile { _, bytes ->
                        parseRulesImport(bytes.decodeToString())?.let { setRules(it) }
                    }
                }) { Text("⬆", style = MaterialTheme.typography.titleMedium) }
                TextButton(
                    onClick = { platformExportFile("routing-rules.json", ("[" + rules().joinToString(",") + "]").encodeToByteArray()) },
                    enabled = rules().isNotEmpty(),
                ) { Text("⬇", style = MaterialTheme.typography.titleMedium) }
            }
            rules().forEachIndexed { i, rule ->
                // The api stats rule is locked on (disabling it breaks traffic
                // accounting); every other rule is enabled unless `enabled:false`.
                val api = isApiRule(rule)
                val on = api || ruleEnabled(rule)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("#${i + 1}  ${ruleSummary(rule)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = on, enabled = !api, onCheckedChange = { v ->
                                val l = rules().toMutableList(); l[i] = jsonPutBool(l[i], listOf("enabled"), v); setRules(l)
                            })
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
                            TextButton(onClick = { editRule = i }) { Text(tr("Edit")) }
                            TextButton(onClick = { setRules(rules().filterIndexed { j, _ -> j != i }) }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { editRule = -1 }, modifier = Modifier.fillMaxWidth()) { Text(tr("Add rule")) }

            XraySection(tr("Balancers"))
            bals().forEachIndexed { i, bal ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(jsonGetString(bal, listOf("tag")).ifBlank { "#${i + 1}" }, Modifier.weight(1f))
                        TextButton(onClick = { editBal = i }) { Text(tr("Edit")) }
                        TextButton(onClick = { setBals(bals().filterIndexed { j, _ -> j != i }) }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            OutlinedButton(onClick = { editBal = -1 }, modifier = Modifier.fillMaxWidth()) { Text(tr("Add balancer")) }
        }
    }

    editRule?.let { idx ->
        RuleDialog(
            initial = if (idx >= 0) rules().getOrElse(idx) { "{}" } else "{}",
            onConfirm = { obj -> val l = rules().toMutableList(); if (idx >= 0) l[idx] = obj else l.add(obj); setRules(l); editRule = null },
            onDismiss = { editRule = null },
        )
    }
    editBal?.let { idx ->
        BalancerDialog(
            initial = if (idx >= 0) bals().getOrElse(idx) { "{}" } else "{}",
            onConfirm = { obj -> val l = bals().toMutableList(); if (idx >= 0) l[idx] = obj else l.add(obj); setBals(l); editBal = null },
            onDismiss = { editBal = null },
        )
    }
}

/** A rule is enabled unless it carries `enabled:false` (panel v3.4.x strips
 *  disabled rules from the running config; older panels ignore the flag). */
private fun ruleEnabled(rule: String): Boolean = jsonGetString(rule, listOf("enabled")) != "false"

/** The internal stats api rule — kept locked on, or traffic accounting breaks. */
private fun isApiRule(rule: String): Boolean =
    jsonGetString(rule, listOf("outboundTag")) == "api" && jsonGetStrings(rule, listOf("inboundTag")).contains("api")

/** Parse a rules-import payload: a bare array, `{rules:[…]}`, or
 *  `{routing:{rules:[…]}}` (matches the panel's flexible import). */
private fun parseRulesImport(text: String): List<String>? {
    val el = try { Json.parseToJsonElement(text.trim()) } catch (e: Exception) { return null }
    val arr = when (el) {
        is JsonArray -> el
        is JsonObject -> (el["rules"] as? JsonArray) ?: ((el["routing"] as? JsonObject)?.get("rules") as? JsonArray)
        else -> null
    } ?: return null
    return arr.map { it.toString() }
}

private fun ruleSummary(rule: String): String {
    val target = jsonGetString(rule, listOf("outboundTag")).ifBlank { jsonGetString(rule, listOf("balancerTag")).let { if (it.isNotBlank()) "⚖ $it" else "" } }
    return target.ifBlank { "→ —" }.let { if (it.startsWith("→")) it else "→ $it" }
}

@Composable
private fun RuleDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var obj by remember { mutableStateOf(initial) }
    fun g(k: String) = jsonGetString(obj, listOf(k))
    fun putOrRemove(k: String, v: String) { obj = if (v.isBlank()) jsonRemove(obj, listOf(k)) else jsonPutString(obj, listOf(k), v) }
    fun csv(k: String) = jsonGetStrings(obj, listOf(k)).joinToString(", ")
    fun putCsv(k: String, v: String) { val p = parseCsvList(v); obj = if (p.isEmpty()) jsonRemove(obj, listOf(k)) else jsonPutStrings(obj, listOf(k), p) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Routing rule")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                XrayLabel(tr("Target (one of)"))
                XrayField(g("outboundTag"), { putOrRemove("outboundTag", it); if (it.isNotBlank()) obj = jsonRemove(obj, listOf("balancerTag")) }, tr("Outbound tag"))
                XrayField(g("balancerTag"), { putOrRemove("balancerTag", it); if (it.isNotBlank()) obj = jsonRemove(obj, listOf("outboundTag")) }, tr("Balancer tag"))
                XrayLabel(tr("Match"))
                RULE_CSV.forEach { (key, label) -> XrayField(csv(key), { putCsv(key, it) }, tr(label) + " " + tr("(comma-separated)")) }
                XrayField(g("port"), { putOrRemove("port", it) }, tr("Port"))
                XrayField(g("sourcePort"), { putOrRemove("sourcePort", it) }, tr("Source port"))
                XrayLabel(tr("Network"))
                XrayChips(NETWORKS, g("network")) { putOrRemove("network", it) }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(jsonPutString(obj, listOf("type"), "field")) }) { Text(tr("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun BalancerDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var obj by remember { mutableStateOf(initial) }
    fun g(k: String) = jsonGetString(obj, listOf(k))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Balancer")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                XrayField(g("tag"), { obj = jsonPutString(obj, listOf("tag"), it) }, tr("Tag"))
                XrayLabel(tr("Strategy"))
                XrayChips(BALANCER_STRATEGY, jsonGetString(obj, listOf("strategy", "type")).ifBlank { "random" }) {
                    obj = jsonPutString(obj, listOf("strategy", "type"), it)
                }
                XrayField(jsonGetStrings(obj, listOf("selector")).joinToString(", "), {
                    val p = parseCsvList(it); obj = if (p.isEmpty()) jsonRemove(obj, listOf("selector")) else jsonPutStrings(obj, listOf("selector"), p)
                }, tr("Selector (comma-separated)"))
                XrayField(g("fallbackTag"), { obj = if (it.isBlank()) jsonRemove(obj, listOf("fallbackTag")) else jsonPutString(obj, listOf("fallbackTag"), it) }, tr("Fallback tag"))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(obj) }, enabled = g("tag").isNotBlank()) { Text(tr("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
