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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.data.json.array
import net.yukh.xui.data.json.asObject
import net.yukh.xui.data.json.boolOrNull
import net.yukh.xui.data.json.child
import net.yukh.xui.data.json.put
import net.yukh.xui.data.json.putArray
import net.yukh.xui.data.json.putBool
import net.yukh.xui.data.json.putString
import net.yukh.xui.data.json.putStrings
import net.yukh.xui.data.json.string
import net.yukh.xui.data.json.strings
import net.yukh.xui.data.json.parseCsv
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.ConfirmDialog
import net.yukh.xui.ui.components.ExportJsonDialog
import net.yukh.xui.ui.components.Field
import net.yukh.xui.ui.components.ImportJsonDialog
import net.yukh.xui.ui.components.LabeledDropdown
import net.yukh.xui.ui.components.SectionTitle

private val ROUTING_DOMAIN_STRATEGY = listOf("AsIs", "IPIfNonMatch", "IPOnDemand")
private val RULE_NETWORK = listOf("", "tcp", "udp", "tcp,udp")
private val BAL_STRATEGY = listOf("random", "roundRobin", "leastLoad", "leastPing")

private fun JsonObject.tagList(arrayKey: String): List<String> =
    child("routing").array(arrayKey).map { it.asObject().string("tag") }.filter { it.isNotBlank() }

private fun JsonObject.outboundTags(): List<String> =
    array("outbounds").map { it.asObject().string("tag") }.filter { it.isNotBlank() }

/** A rule is enabled unless it carries `enabled: false` (panel v3.4.x strips
 *  disabled rules from the running config; older panels ignore the flag). */
private fun JsonObject.ruleEnabled(): Boolean = boolOrNull("enabled") != false

/** The internal stats api rule — its enabled state must stay locked on, or
 *  traffic accounting breaks (mirrors the panel's isApiRule). */
private fun JsonObject.isApiRule(): Boolean =
    string("outboundTag") == "api" && strings("inboundTag").contains("api")

/** Parse a rules-import payload: a bare array, `{rules:[…]}`, or
 *  `{routing:{rules:[…]}}` (matches the panel's flexible import). */
private fun parseRulesImport(text: String): List<JsonElement>? {
    val el = try { Json.parseToJsonElement(text.trim()) } catch (e: Exception) { return null }
    val arr = when (el) {
        is JsonArray -> el
        is JsonObject -> (el["rules"] as? JsonArray)
            ?: ((el["routing"] as? JsonObject)?.get("rules") as? JsonArray)
        else -> null
    }
    return arr?.toList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingScreen(onClose: () -> Unit, vm: RoutingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.savedMessage) { state.savedMessage?.let { snackbar.showSnackbar(it); vm.dismissSavedMessage() } }
    LaunchedEffect(state.error) { if (state.available) state.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }

    val cfg = state.config
    val rule = state.editingRule
    if (rule != null) {
        RuleEditor(
            draft = rule.draft, isNew = rule.isNew,
            outboundTags = cfg.outboundTags(), balancerTags = cfg.tagList("balancers"),
            inboundTags = cfg.array("inbounds").map { it.asObject().string("tag") }.filter { it.isNotBlank() },
            onChange = vm::updateRuleDraft, onCancel = vm::closeRule,
            onDone = {
                val routing = cfg.child("routing")
                val arr = routing.array("rules").toMutableList()
                val clean = rule.draft.putString("type", "field")
                if (rule.isNew) arr.add(clean) else arr[rule.index] = clean
                vm.update(cfg.put("routing", routing.putArray("rules", arr)))
                vm.closeRule()
            },
        )
        return
    }
    val bal = state.editingBalancer
    if (bal != null) {
        BalancerEditor(
            draft = bal.draft, isNew = bal.isNew, outboundTags = cfg.outboundTags(),
            onChange = vm::updateBalDraft, onCancel = vm::closeBalancer,
            onDone = {
                val routing = cfg.child("routing")
                val arr = routing.array("balancers").toMutableList()
                if (bal.isNew) arr.add(bal.draft) else arr[bal.index] = bal.draft
                vm.update(cfg.put("routing", routing.putArray("balancers", arr)))
                vm.closeBalancer()
            },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(tr("Routing")) },
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
                else -> RoutingBody(cfg, state.inboundTags, vm)
            }
        }
    }
}

@Composable
private fun RouteTestDialog(
    vm: RoutingViewModel,
    inboundTags: List<String>,
    networkOptions: List<String>,
    onClose: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var dest by remember { mutableStateOf("") }
    var inboundTag by remember(inboundTags) { mutableStateOf(inboundTags.firstOrNull() ?: "") }
    var network by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.clearRouteTest(); onClose() },
        title = { Text(tr("Test route")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dest,
                    onValueChange = { dest = it },
                    label = { Text(tr("Domain or IP")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text(tr("Port (optional)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (inboundTags.isNotEmpty()) {
                    LabeledDropdown(tr("Inbound"), inboundTag, inboundTags) { inboundTag = it }
                }
                LabeledDropdown(tr("Network"), network, networkOptions) { network = it }
                when {
                    state.routeTesting -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    state.routeTestError != null -> Text(
                        "✗ ${state.routeTestError}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.routeTestResult != null -> {
                        val r = state.routeTestResult!!
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
                onClick = { vm.testRoute(dest, inboundTag, network, port) },
                enabled = dest.isNotBlank() && !state.routeTesting,
            ) { Text(tr("Test")) }
        },
        dismissButton = { TextButton(onClick = { vm.clearRouteTest(); onClose() }) { Text(tr("Close")) } },
    )
}

@Composable
private fun RoutingBody(cfg: JsonObject, inboundTags: List<String>, vm: RoutingViewModel) {
    val routing = cfg.child("routing")
    val rules = routing.array("rules")
    val balancers = routing.array("balancers")
    var pendingRule by remember { mutableStateOf<Int?>(null) }
    var pendingBal by remember { mutableStateOf<Int?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showRouteTest by remember { mutableStateOf(false) }

    fun setRules(items: List<kotlinx.serialization.json.JsonElement>) = vm.update(cfg.put("routing", routing.putArray("rules", items)))
    fun setBalancers(items: List<kotlinx.serialization.json.JsonElement>) = vm.update(cfg.put("routing", routing.putArray("balancers", items)))

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LabeledDropdown(tr("Routing strategy"), routing.string("domainStrategy").ifBlank { "AsIs" }, ROUTING_DOMAIN_STRATEGY) {
            vm.update(cfg.put("routing", routing.putString("domainStrategy", it)))
        }

        OutlinedButton(onClick = { showRouteTest = true }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Test route"))
        }
        if (showRouteTest) {
            RouteTestDialog(vm = vm, inboundTags = inboundTags, networkOptions = RULE_NETWORK, onClose = { showRouteTest = false })
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(tr("Routing rules"))
            Box(modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { showImport = true }) {
                Icon(Icons.Outlined.FileUpload, contentDescription = tr("Import"))
            }
            FilledTonalIconButton(onClick = { showExport = true }, enabled = rules.isNotEmpty()) {
                Icon(Icons.Outlined.FileDownload, contentDescription = tr("Export"))
            }
        }
        rules.forEachIndexed { i, el ->
            val r = el.asObject()
            val api = r.isApiRule()
            // The api stats rule is always active and locked on; others follow `enabled`.
            val on = api || r.ruleEnabled()
            val target = r.string("outboundTag").ifBlank { r.string("balancerTag").let { if (it.isNotBlank()) "⚖ $it" else "—" } }
            val src = (r.strings("inboundTag") + r.strings("domain") + r.strings("ip")).firstOrNull().orEmpty()
            Card(modifier = Modifier.fillMaxWidth().clickable { vm.openRule(i, r, isNew = false) }) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // The api stats rule stays locked on — render it as a normal "on"
                    // switch (a disabled Switch misleadingly draws the thumb off) and
                    // just swallow taps on it.
                    Switch(
                        checked = on,
                        onCheckedChange = { v ->
                            if (!api) { val a = rules.toMutableList(); a[i] = r.putBool("enabled", v); setRules(a) }
                        },
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp).alpha(if (on) 1f else 0.45f)) {
                        Text("#${i + 1}  → $target", style = MaterialTheme.typography.titleSmall)
                        if (src.isNotBlank()) Text(src, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { val a = rules.toMutableList(); if (i > 0) { val x = a.removeAt(i); a.add(i - 1, x); setRules(a) } }, enabled = i > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = tr("Move up"))
                    }
                    IconButton(onClick = { val a = rules.toMutableList(); if (i < a.size - 1) { val x = a.removeAt(i); a.add(i + 1, x); setRules(a) } }, enabled = i < rules.size - 1) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = tr("Move down"))
                    }
                    IconButton(onClick = { pendingRule = i }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Delete")) }
                }
            }
        }
        OutlinedButton(onClick = { vm.openRule(-1, JsonObject(emptyMap()).putString("type", "field"), isNew = true) }) {
            Icon(Icons.Filled.Add, contentDescription = null); Text("  " + tr("Add rule"))
        }

        SectionTitle(tr("Balancers"))
        balancers.forEachIndexed { i, el ->
            val b = el.asObject()
            Card(modifier = Modifier.fillMaxWidth().clickable { vm.openBalancer(i, b, isNew = false) }) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(b.string("tag").ifBlank { "(no tag)" }, style = MaterialTheme.typography.titleSmall)
                        Text(b.strings("selector").joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { pendingBal = i }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Delete")) }
                }
            }
        }
        OutlinedButton(onClick = { vm.openBalancer(-1, JsonObject(emptyMap()), isNew = true) }) {
            Icon(Icons.Filled.Add, contentDescription = null); Text("  " + tr("Add balancer"))
        }
    }

    pendingRule?.let { idx ->
        ConfirmDialog(tr("Delete rule?"), "#${idx + 1}", tr("Delete"), destructive = true,
            onConfirm = { pendingRule = null; val a = rules.toMutableList(); if (idx in a.indices) a.removeAt(idx); setRules(a) },
            onDismiss = { pendingRule = null })
    }
    pendingBal?.let { idx ->
        ConfirmDialog(tr("Delete balancer?"), balancers.getOrNull(idx)?.asObject()?.string("tag").orEmpty(), tr("Delete"), destructive = true,
            onConfirm = { pendingBal = null; val a = balancers.toMutableList(); if (idx in a.indices) a.removeAt(idx); setBalancers(a) },
            onDismiss = { pendingBal = null })
    }

    if (showExport) {
        ExportJsonDialog(
            title = tr("Export rules"),
            json = xrayPrettyJson.encodeToString(JsonElement.serializer(), rules),
            onDismiss = { showExport = false },
        )
    }
    if (showImport) {
        val invalidMsg = tr("Invalid JSON")
        val importedLabel = tr("Imported")
        ImportJsonDialog(
            title = tr("Import rules"),
            onImport = { txt ->
                val parsed = parseRulesImport(txt) ?: return@ImportJsonDialog invalidMsg
                setRules(rules.toList() + parsed)
                vm.info("$importedLabel: ${parsed.size}")
                null
            },
            onDismiss = { showImport = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditor(
    draft: JsonObject, isNew: Boolean,
    outboundTags: List<String>, balancerTags: List<String>, inboundTags: List<String>,
    onChange: (JsonObject) -> Unit, onCancel: () -> Unit, onDone: () -> Unit,
) {
    fun setArr(key: String, csv: String) { val l = parseCsv(csv); onChange(if (l.isEmpty()) draft.put(key, null) else draft.putStrings(key, l)) }
    fun setStr(key: String, v: String) = onChange(if (v.isBlank()) draft.put(key, null) else draft.putString(key, v))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) tr("New rule") else tr("Edit rule")) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close")) } },
                actions = { TextButton(onClick = onDone) { Text(tr("Done")) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(tr("Target"))
            LabeledDropdown(tr("Outbound tag"), draft.string("outboundTag"), listOf("") + outboundTags) {
                onChange(if (it.isBlank()) draft.put("outboundTag", null) else draft.putString("outboundTag", it).put("balancerTag", null))
            }
            LabeledDropdown(tr("Balancer tag"), draft.string("balancerTag"), listOf("") + balancerTags) {
                onChange(if (it.isBlank()) draft.put("balancerTag", null) else draft.putString("balancerTag", it).put("outboundTag", null))
            }
            SectionTitle(tr("Match"))
            Field(tr("Inbound tags (comma-separated)"), draft.strings("inboundTag").joinToString(", ")) { setArr("inboundTag", it) }
            Field(tr("Domain (comma-separated)"), draft.strings("domain").joinToString(", ")) { setArr("domain", it) }
            Field(tr("Destination IP (comma-separated)"), draft.strings("ip").joinToString(", ")) { setArr("ip", it) }
            Field(tr("Port"), draft.string("port")) { setStr("port", it) }
            Field(tr("Source IPs (comma-separated)"), draft.strings("sourceIP").joinToString(", ")) { setArr("sourceIP", it) }
            Field(tr("Source port"), draft.string("sourcePort")) { setStr("sourcePort", it) }
            LabeledDropdown(tr("Network"), draft.string("network"), RULE_NETWORK) { setStr("network", it) }
            Field(tr("Protocol (comma-separated)"), draft.strings("protocol").joinToString(", ")) { setArr("protocol", it) }
            Field(tr("User (comma-separated)"), draft.strings("user").joinToString(", ")) { setArr("user", it) }
            Field(tr("VLESS route"), draft.string("vlessRoute")) { setStr("vlessRoute", it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BalancerEditor(
    draft: JsonObject, isNew: Boolean, outboundTags: List<String>,
    onChange: (JsonObject) -> Unit, onCancel: () -> Unit, onDone: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) tr("New balancer") else tr("Edit balancer")) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close")) } },
                actions = { TextButton(onClick = onDone) { Text(tr("Done")) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(tr("Tag"), draft.string("tag")) { onChange(draft.putString("tag", it)) }
            val strategy = draft.child("strategy").string("type").ifBlank { "random" }
            LabeledDropdown(tr("Strategy"), strategy, BAL_STRATEGY) {
                onChange(if (it == "random") draft.put("strategy", null) else draft.put("strategy", JsonObject(emptyMap()).putString("type", it)))
            }
            Field(tr("Selector (comma-separated)"), draft.strings("selector").joinToString(", ")) {
                val l = parseCsv(it); onChange(draft.putStrings("selector", l))
            }
            LabeledDropdown(tr("Fallback"), draft.string("fallbackTag"), listOf("") + outboundTags) {
                onChange(if (it.isBlank()) draft.put("fallbackTag", null) else draft.putString("fallbackTag", it))
            }
        }
    }
}
