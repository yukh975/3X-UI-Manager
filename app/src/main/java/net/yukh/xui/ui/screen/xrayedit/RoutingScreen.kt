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
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.data.json.array
import net.yukh.xui.data.json.asObject
import net.yukh.xui.data.json.child
import net.yukh.xui.data.json.put
import net.yukh.xui.data.json.putArray
import net.yukh.xui.data.json.putString
import net.yukh.xui.data.json.putStrings
import net.yukh.xui.data.json.string
import net.yukh.xui.data.json.strings
import net.yukh.xui.data.json.parseCsv
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.ConfirmDialog
import net.yukh.xui.ui.components.Field
import net.yukh.xui.ui.components.LabeledDropdown
import net.yukh.xui.ui.components.SectionTitle

private val ROUTING_DOMAIN_STRATEGY = listOf("AsIs", "IPIfNonMatch", "IPOnDemand")
private val RULE_NETWORK = listOf("", "tcp", "udp", "tcp,udp")
private val BAL_STRATEGY = listOf("random", "roundRobin", "leastLoad", "leastPing")

private fun JsonObject.tagList(arrayKey: String): List<String> =
    child("routing").array(arrayKey).map { it.asObject().string("tag") }.filter { it.isNotBlank() }

private fun JsonObject.outboundTags(): List<String> =
    array("outbounds").map { it.asObject().string("tag") }.filter { it.isNotBlank() }

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
                else -> RoutingBody(cfg, vm)
            }
        }
    }
}

@Composable
private fun RoutingBody(cfg: JsonObject, vm: RoutingViewModel) {
    val routing = cfg.child("routing")
    val rules = routing.array("rules")
    val balancers = routing.array("balancers")
    var pendingRule by remember { mutableStateOf<Int?>(null) }
    var pendingBal by remember { mutableStateOf<Int?>(null) }

    fun setRules(items: List<kotlinx.serialization.json.JsonElement>) = vm.update(cfg.put("routing", routing.putArray("rules", items)))
    fun setBalancers(items: List<kotlinx.serialization.json.JsonElement>) = vm.update(cfg.put("routing", routing.putArray("balancers", items)))

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LabeledDropdown(tr("Routing strategy"), routing.string("domainStrategy").ifBlank { "AsIs" }, ROUTING_DOMAIN_STRATEGY) {
            vm.update(cfg.put("routing", routing.putString("domainStrategy", it)))
        }

        SectionTitle(tr("Routing rules"))
        rules.forEachIndexed { i, el ->
            val r = el.asObject()
            val target = r.string("outboundTag").ifBlank { r.string("balancerTag").let { if (it.isNotBlank()) "⚖ $it" else "—" } }
            val src = (r.strings("inboundTag") + r.strings("domain") + r.strings("ip")).firstOrNull().orEmpty()
            Card(modifier = Modifier.fillMaxWidth().clickable { vm.openRule(i, r, isNew = false) }) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("→ $target", style = MaterialTheme.typography.titleSmall)
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
