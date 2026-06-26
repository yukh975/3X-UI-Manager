package net.yukh.xui.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.yukh.xui.shared.dto.Client
import net.yukh.xui.shared.dto.InboundSlim
import net.yukh.xui.shared.dto.Node
import net.yukh.xui.shared.dto.TrafficSummary

@Composable
private fun ListScaffold(
    title: String,
    count: Int,
    empty: String,
    onAdd: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$title ($count)", style = MaterialTheme.typography.headlineSmall)
            if (onAdd != null) TextButton(onClick = onAdd) { Text("+ " + tr("Add")) }
        }
        if (count == 0) {
            Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        } else {
            content()
        }
    }
}

@Composable
private fun rowCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val base = Modifier.fillMaxWidth()
    Card(modifier = if (onClick != null) base.clickable(onClick = onClick) else base) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
fun InboundsListScreen(
    items: List<InboundSlim>,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    speeds: Map<Int, Pair<Long, Long>> = emptyMap(),
) {
    ListScaffold("Inbounds", items.size, tr("No inbounds yet."), onAdd = onAdd) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
            items(items, key = { it.id }) { ib ->
                rowCard(onClick = { onEdit(ib.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ib.remark.ifBlank { "inbound #${ib.id}" }, style = MaterialTheme.typography.titleMedium)
                        Switch(checked = ib.enable, onCheckedChange = { onToggle(ib.id, it) })
                    }
                    Text("${ib.protocol.uppercase().ifBlank { "?" }} · ${ib.listen.ifBlank { "*" }}:${ib.port}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("↑ ${ib.up.formatBytes()}  ↓ ${ib.down.formatBytes()}", style = MaterialTheme.typography.labelMedium)
                    speeds[ib.id]?.let { (up, down) ->
                        if (up > 0 || down > 0) Text(
                            "↑ ${up.formatBytes()}/s  ↓ ${down.formatBytes()}/s",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** Quick status filter for the client list. */
private enum class ClientFilter { ALL, ENABLED, DISABLED, ONLINE, DEPLETED }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClientsListScreen(
    items: List<Client>,
    onlineEmails: Set<String>,
    onAdd: () -> Unit,
    onEdit: (Client) -> Unit,
    onToggle: (Client, Boolean) -> Unit,
    onSearch: () -> Unit = {},
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeleteOrphans: () -> Unit,
    bulkBusy: Boolean = false,
    onBulkEnable: (List<String>) -> Unit = {},
    onBulkDisable: (List<String>) -> Unit = {},
    onBulkAdjust: (List<String>, Int, Long, String) -> Unit = { _, _, _, _ -> },
    onBulkDelete: (List<String>) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ClientFilter.ALL) }
    var group by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAdjust by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    fun exitSelection() { selectionMode = false; selected = emptySet() }

    val groups = remember(items) { items.mapNotNull { it.group.ifBlank { null } }.distinct().sorted() }
    fun depleted(c: Client) = c.quota > 0 && (c.up + c.down) >= c.quota
    val filtered = items.filter { c ->
        (query.isBlank() || c.email.contains(query, ignoreCase = true)) &&
            (group == null || c.group == group) &&
            when (filter) {
                ClientFilter.ALL -> true
                ClientFilter.ENABLED -> c.enable
                ClientFilter.DISABLED -> !c.enable
                ClientFilter.ONLINE -> c.email in onlineEmails
                ClientFilter.DEPLETED -> depleted(c)
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (selectionMode) {
            ClientSelectionBar(
                count = selected.size,
                busy = bulkBusy,
                onClose = { exitSelection() },
                onSelectAll = { selected = filtered.map { it.email }.toSet() },
                onEnable = { onBulkEnable(selected.toList()); exitSelection() },
                onDisable = { onBulkDisable(selected.toList()); exitSelection() },
                onAdjust = { showAdjust = true },
                onDelete = { showDelete = true },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${tr("Clients")} (${filtered.size}/${items.size})", style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onAdd) { Text("+ " + tr("Add")) }
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("⋮") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(tr("Export clients")) }, onClick = { menuOpen = false; onExport() })
                            DropdownMenuItem(text = { Text(tr("Import clients")) }, onClick = { menuOpen = false; onImport() })
                            DropdownMenuItem(text = { Text(tr("Delete unbound clients")) }, onClick = { menuOpen = false; onDeleteOrphans() })
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(tr("Search by email"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        // Status chips.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClientFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(filterLabel(f)) })
            }
        }
        // Group chips (only when clients carry groups).
        if (groups.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = group == null, onClick = { group = null }, label = { Text(tr("All groups")) })
                groups.forEach { g ->
                    FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) })
                }
            }
        }

        if (filtered.isEmpty()) {
            Text(tr("No clients yet."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                items(filtered, key = { it.id.toString() + it.email }) { c ->
                    val isSel = c.email in selected
                    fun toggleSel() {
                        selected = if (isSel) selected - c.email else selected + c.email
                        if (selected.isEmpty()) selectionMode = false
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().combinedClickable(
                            onClick = { if (selectionMode) toggleSel() else onEdit(c) },
                            onLongClick = { if (!selectionMode) { selectionMode = true }; toggleSel() },
                        ),
                        colors = if (isSel) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (selectionMode) Checkbox(checked = isSel, onCheckedChange = { toggleSel() })
                                    Text(c.email.ifBlank { "#${c.id}" }, style = MaterialTheme.typography.titleMedium)
                                    if (c.email in onlineEmails) {
                                        Text("●", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                if (!selectionMode) Switch(checked = c.enable, onCheckedChange = { onToggle(c, it) })
                            }
                            Text("↑ ${c.up.formatBytes()}  ↓ ${c.down.formatBytes()}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    if (showAdjust) {
        BulkAdjustDialog(
            count = selected.size,
            onApply = { days, bytes, flow -> onBulkAdjust(selected.toList(), days, bytes, flow); showAdjust = false; exitSelection() },
            onDismiss = { showAdjust = false },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(tr("Delete selected clients?")) },
            text = { Text("${selected.size} ${tr("selected")}") },
            confirmButton = { TextButton(onClick = { onBulkDelete(selected.toList()); showDelete = false; exitSelection() }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(tr("Cancel")) } },
        )
    }
}

@Composable
private fun ClientSelectionBar(
    count: Int,
    busy: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onAdjust: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onClose) { Text("✕") }
        Text("$count ${tr("selected")}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        TextButton(onClick = onSelectAll) { Text(tr("All")) }
        Box {
            TextButton(onClick = { menu = true }, enabled = !busy) { Text("⋮") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(tr("Enable")) }, onClick = { menu = false; onEnable() })
                DropdownMenuItem(text = { Text(tr("Disable")) }, onClick = { menu = false; onDisable() })
                DropdownMenuItem(text = { Text(tr("Adjust") + "…") }, onClick = { menu = false; onAdjust() })
                DropdownMenuItem(text = { Text(tr("Delete")) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun BulkAdjustDialog(count: Int, onApply: (Int, Long, String) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf("") }
    var gb by remember { mutableStateOf("") }
    var flow by remember { mutableStateOf("") }
    // API flow value → display label (matches the panel's bulk Adjust flow set).
    val flowLabels = linkedMapOf(
        "" to tr("No change"),
        "none" to tr("Clear flow"),
        "xtls-rprx-vision" to "xtls-rprx-vision",
        "xtls-rprx-vision-udp443" to "xtls-rprx-vision-udp443",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${tr("Adjust")} ($count)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text(tr("Add days (+/-)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = gb,
                    onValueChange = { gb = it.filter { c -> c.isDigit() || c == '-' || c == '.' } },
                    label = { Text(tr("Add traffic (GB, +/-)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(tr("Set flow"), style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    flowLabels.forEach { (key, label) ->
                        FilterChip(selected = flow == key, onClick = { flow = key }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val addDays = days.toIntOrNull() ?: 0
                val addBytes = ((gb.toDoubleOrNull() ?: 0.0) * 1024.0 * 1024.0 * 1024.0).toLong()
                onApply(addDays, addBytes, flow)
            }) { Text(tr("Apply")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun filterLabel(f: ClientFilter): String = when (f) {
    ClientFilter.ALL -> tr("All")
    ClientFilter.ENABLED -> tr("Enabled")
    ClientFilter.DISABLED -> tr("Disabled")
    ClientFilter.ONLINE -> tr("Online")
    ClientFilter.DEPLETED -> tr("Depleted")
}

@Composable
fun NodesListScreen(
    items: List<Node>,
    traffic: Map<Int, TrafficSummary>,
    masterVersion: String,
    updatingNodeIds: Set<Int>,
    onAdd: () -> Unit,
    onEdit: (Node) -> Unit,
    onUpdateNode: (Node, Boolean) -> Unit,
    onCopyCa: () -> Unit,
    onSetTrustCa: () -> Unit,
) {
    var pendingUpdate by remember { mutableStateOf<Node?>(null) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${tr("Nodes")} (${items.size})", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onAdd) { Text("+ " + tr("Add")) }
        }

        MtlsCard(onCopyCa = onCopyCa, onSetTrustCa = onSetTrustCa)

        if (items.isEmpty()) {
            Text(tr("No nodes yet."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                items(items, key = { it.id }) { n ->
                    rowCard(onClick = { onEdit(n) }) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(n.remark.ifBlank { n.name }.ifBlank { "#${n.id}" }, style = MaterialTheme.typography.titleMedium)
                            Text(if (n.online) "online" else "offline",
                                color = if (n.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        Text("${n.scheme}://${n.address}:${n.port}", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (n.online) {
                            Text("CPU: ${n.cpuPct.formatPercent()} · RAM: ${n.memPct.formatPercent()}", style = MaterialTheme.typography.labelMedium)
                            Text("${n.inboundCount} inbounds · ${n.clientCount} clients", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (n.panelVersion.isNotBlank()) {
                                // Offer self-update when the node runs an older 3x-ui than the master panel.
                                val outdated = masterVersion.isNotBlank() && n.panelVersion != masterVersion
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("3x-ui: v${n.panelVersion}", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    when {
                                        n.id in updatingNodeIds -> Text("…", style = MaterialTheme.typography.labelMedium)
                                        outdated -> TextButton(onClick = { pendingUpdate = n }) {
                                            Text("${tr("Update to")} v$masterVersion")
                                        }
                                    }
                                }
                            }
                        }
                        traffic[n.id]?.let {
                            Text("${tr("Traffic this month")}: ${it.bytes.formatBytes()}",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    pendingUpdate?.let { node ->
        var dev by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pendingUpdate = null },
            title = { Text(tr("Update node?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { dev = !dev },
                    ) {
                        Checkbox(checked = dev, onCheckedChange = { dev = it })
                        Text(tr("Update to dev build (latest commit)"))
                    }
                    if (dev) Text(tr("Dev builds are unstable."),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { onUpdateNode(node, dev); pendingUpdate = null }) { Text(tr("Update")) } },
            dismissButton = { TextButton(onClick = { pendingUpdate = null }) { Text(tr("Cancel")) } },
        )
    }
}

/** Panel-wide mTLS controls: share this panel's CA (so a parent can trust it),
 *  and set the parent CA this panel trusts when it acts as a node. Panel v3.4. */
@Composable
private fun MtlsCard(onCopyCa: () -> Unit, onSetTrustCa: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("mTLS (node certificates)"), style = MaterialTheme.typography.labelMedium)
                Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCopyCa) { Text(tr("Export this panel's CA")) }
                    TextButton(onClick = onSetTrustCa) { Text(tr("Set trusted parent CA")) }
                }
            }
        }
    }
}
