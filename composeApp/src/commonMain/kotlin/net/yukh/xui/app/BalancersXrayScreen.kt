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
import net.yukh.xui.shared.json.jsonGetObjectList
import net.yukh.xui.shared.json.jsonGetString
import net.yukh.xui.shared.json.jsonGetStrings
import net.yukh.xui.shared.json.jsonPutString
import net.yukh.xui.shared.json.jsonPutStrings
import net.yukh.xui.shared.json.jsonRemove
import net.yukh.xui.shared.json.jsonSetObjectList
import net.yukh.xui.shared.json.normalizeBalancer
import net.yukh.xui.shared.json.parseCsvList
import net.yukh.xui.shared.json.syncObservatories

private val BALANCER_STRATEGY = listOf("random", "roundRobin", "leastLoad", "leastPing")

/** Balancers (routing.balancers) + the Observatory probe settings, edited on the
 *  one config JSON string like the Routing screen. */
@Composable
fun BalancersXrayScreen(
    configJson: String,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    onConfigChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    observatoryUnsaved: Boolean,
    onSaveObservatory: () -> Unit,
) {
    var editBal by remember { mutableStateOf<Int?>(null) }

    fun bals() = jsonGetObjectList(configJson, listOf("routing", "balancers"))
    // Like the panel, every balancer change re-syncs the observers Xray needs to start.
    fun setBals(l: List<String>) = onConfigChange(syncObservatories(jsonSetObjectList(configJson, listOf("routing", "balancers"), l)))

    Column(Modifier.fillMaxSize()) {
        XrayEditHeader(tr("Balancers"), saving, onCancel, onSave, canSave = !loading)
        if (error != null) Text(error, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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

            ObservatorySection(configJson, onConfigChange, observatoryUnsaved, saving, onSaveObservatory)
        }
    }

    editBal?.let { idx ->
        BalancerDialog(
            initial = if (idx >= 0) bals().getOrElse(idx) { "{}" } else "{}",
            onConfirm = { obj -> val l = bals().toMutableList(); if (idx >= 0) l[idx] = obj else l.add(obj); setBals(l); editBal = null },
            onDismiss = { editBal = null },
        )
    }
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
        confirmButton = { TextButton(onClick = { onConfirm(normalizeBalancer(obj)) }, enabled = g("tag").isNotBlank()) { Text(tr("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
