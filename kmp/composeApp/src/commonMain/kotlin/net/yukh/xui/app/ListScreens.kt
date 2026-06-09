package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.yukh.xui.shared.dto.Client
import net.yukh.xui.shared.dto.InboundSlim
import net.yukh.xui.shared.dto.Node

@Composable
private fun ListScaffold(title: String, count: Int, empty: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("$title ($count)", style = MaterialTheme.typography.headlineSmall)
        if (count == 0) {
            Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        } else {
            content()
        }
    }
}

@Composable
private fun rowCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
fun InboundsListScreen(items: List<InboundSlim>) {
    ListScaffold("Inbounds", items.size, "No inbounds") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
            items(items, key = { it.id }) { ib ->
                rowCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(ib.remark.ifBlank { "inbound #${ib.id}" }, style = MaterialTheme.typography.titleMedium)
                        Text(if (ib.enable) "on" else "off",
                            color = if (ib.enable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Text("${ib.protocol.uppercase().ifBlank { "?" }} · ${ib.listen.ifBlank { "*" }}:${ib.port}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("↑ ${ib.up.formatBytes()}  ↓ ${ib.down.formatBytes()}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun ClientsListScreen(items: List<Client>) {
    ListScaffold("Clients", items.size, "No clients") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
            items(items, key = { it.id.toString() + it.email }) { c ->
                rowCard {
                    Text(c.email.ifBlank { "#${c.id}" }, style = MaterialTheme.typography.titleMedium)
                    Text("↑ ${c.up.formatBytes()}  ↓ ${c.down.formatBytes()}", style = MaterialTheme.typography.labelMedium)
                    val seen = if (c.lastOnline <= 0L) "Last seen: Never" else "Online"
                    Text(seen, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun NodesListScreen(items: List<Node>) {
    ListScaffold("Nodes", items.size, "No nodes") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
            items(items, key = { it.id }) { n ->
                rowCard {
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
                }
            }
        }
    }
}
