package net.yukh.xui.app

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.shared.dto.InboundSlim
import net.yukh.xui.shared.dto.RouteTestResult
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
private val RULE_CSV = listOf(
    "inboundTag" to "Inbound tags",
    "domain" to "Domains",
    "ip" to "IPs",
    "source" to "Source IPs",
    "user" to "Users",
    "protocol" to "Protocols",
)

/** Structured Routing section: domainStrategy + rules, edited on the one config
 *  JSON string. Balancers + observatory have their own screen (BalancersXrayScreen). */
@Composable
fun RoutingXrayScreen(
    configJson: String,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    onConfigChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    inbounds: List<InboundSlim> = emptyList(),
    onRouteTest: (suspend (String, String, String, String, String) -> RouteTestResult?)? = null,
    /** Panel v3.8.0+: a rule can carry a panel-only comment. */
    panel380: Boolean = false,
) {
    var editRule by remember { mutableStateOf<Int?>(null) }
    var showRouteTest by remember { mutableStateOf(false) }

    fun rules() = jsonGetObjectList(configJson, listOf("routing", "rules"))
    fun setRules(l: List<String>) = onConfigChange(jsonSetObjectList(configJson, listOf("routing", "rules"), l))

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
            if (jsonGetString(configJson, listOf("routing", "script")).isNotBlank()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        tr("A routing script is set under General / Logs, so Xray ignores these rules."),
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            XrayLabel(tr("Routing strategy"))
            XrayChips(ROUTING_STRATEGY, jsonGetString(configJson, listOf("routing", "domainStrategy")).ifBlank { "AsIs" }) {
                onConfigChange(jsonPutString(configJson, listOf("routing", "domainStrategy"), it))
            }

            if (onRouteTest != null) {
                OutlinedButton(onClick = { showRouteTest = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Test route"))
                }
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
                        val note = jsonGetString(rule, listOf("comment"))
                        if (note.isNotBlank()) {
                            Text(
                                note,
                                modifier = Modifier.padding(end = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
                            TextButton(onClick = { editRule = i }) { Text(tr("Edit")) }
                            TextButton(onClick = { setRules(rules().filterIndexed { j, _ -> j != i }) }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { editRule = -1 }, modifier = Modifier.fillMaxWidth()) { Text(tr("Add rule")) }
        }
    }

    editRule?.let { idx ->
        RuleDialog(
            initial = if (idx >= 0) rules().getOrElse(idx) { "{}" } else "{}",
            showComment = panel380,
            onConfirm = { obj -> val l = rules().toMutableList(); if (idx >= 0) l[idx] = obj else l.add(obj); setRules(l); editRule = null },
            onDismiss = { editRule = null },
        )
    }
    if (showRouteTest && onRouteTest != null) {
        RouteTestDialog(inbounds = inbounds, onRouteTest = onRouteTest, onDismiss = { showRouteTest = false })
    }
}

@Composable
private fun RouteTestDialog(
    inbounds: List<InboundSlim>,
    onRouteTest: suspend (String, String, String, String, String) -> RouteTestResult?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Show the human remark (falling back to the tag) but send the tag.
    val inboundOptions = remember(inbounds) {
        inbounds.filter { it.tag.isNotBlank() }.map { it.tag to it.remark.ifBlank { it.tag } }
    }
    var dest by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var network by remember { mutableStateOf("") }
    var inboundTag by remember(inboundOptions) { mutableStateOf(inboundOptions.firstOrNull()?.first ?: "") }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RouteTestResult?>(null) }
    var failed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Test route")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                XrayField(dest, { dest = it }, tr("Domain or IP"))
                XrayField(port, { port = it.filter(Char::isDigit) }, tr("Port (optional)"))
                if (inboundOptions.isNotEmpty()) {
                    XrayLabel(tr("Inbound"))
                    val labels = inboundOptions.map { it.second }
                    val selectedLabel = inboundOptions.firstOrNull { it.first == inboundTag }?.second ?: labels.firstOrNull().orEmpty()
                    XrayChips(labels, selectedLabel) { picked ->
                        inboundTag = inboundOptions.firstOrNull { it.second == picked }?.first ?: inboundTag
                    }
                }
                XrayLabel(tr("Network"))
                XrayChips(NETWORKS, network) { network = it }
                when {
                    running -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    failed -> Text(tr("Test request failed."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    result != null -> {
                        val r = result!!
                        val target = if (r.matched && r.outboundTag.isNotBlank()) r.outboundTag else tr("default outbound")
                        Column {
                            Text("→ $target", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            if (r.groupTags.isNotEmpty()) {
                                Text(
                                    "${tr("via")}: ${r.groupTags.joinToString(" → ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!r.matched) {
                                Text(
                                    tr("No rule matched — uses the default outbound."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = dest.isNotBlank() && !running,
                onClick = {
                    val d = dest.trim()
                    val isIp = d.none { it.isLetter() } && (d.contains('.') || d.contains(':'))
                    running = true; failed = false; result = null
                    scope.launch {
                        val r = runCatching {
                            onRouteTest(if (isIp) "" else d, if (isIp) d else "", port.trim(), network, inboundTag)
                        }.getOrNull()
                        result = r; failed = r == null; running = false
                    }
                },
            ) { Text(tr("Test")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
    )
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
private fun RuleDialog(initial: String, showComment: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
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
                if (showComment) {
                    // Panel-only note (v3.8.0): the panel strips it before the config reaches Xray.
                    XrayField(g("comment"), { putOrRemove("comment", it.take(200)) }, tr("Comment (panel only)"))
                }
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
