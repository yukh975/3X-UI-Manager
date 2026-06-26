package net.yukh.xui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
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
fun InboundsListScreen(items: List<InboundSlim>, onAdd: () -> Unit, onEdit: (Int) -> Unit, onToggle: (Int, Boolean) -> Unit) {
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
                }
            }
        }
    }
}

/** Quick status filter for the client list. */
private enum class ClientFilter { ALL, ENABLED, DISABLED, ONLINE, DEPLETED }

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
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ClientFilter.ALL) }
    var group by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

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
                    rowCard(onClick = { onEdit(c) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(c.email.ifBlank { "#${c.id}" }, style = MaterialTheme.typography.titleMedium)
                                if (c.email in onlineEmails) {
                                    Text("●", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Switch(checked = c.enable, onCheckedChange = { onToggle(c, it) })
                        }
                        Text("↑ ${c.up.formatBytes()}  ↓ ${c.down.formatBytes()}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
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
fun NodesListScreen(items: List<Node>, traffic: Map<Int, TrafficSummary>, onAdd: () -> Unit, onEdit: (Node) -> Unit) {
    ListScaffold(tr("Nodes"), items.size, tr("No nodes yet."), onAdd = onAdd) {
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
                            Text("3x-ui: v${n.panelVersion}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
