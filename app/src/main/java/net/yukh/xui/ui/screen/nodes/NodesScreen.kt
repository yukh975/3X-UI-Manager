package net.yukh.xui.ui.screen.nodes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.yukh.xui.data.api.dto.Node
import net.yukh.xui.data.repo.ServerTraffic
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.format.formatBytes
import net.yukh.xui.ui.format.formatPercent
import net.yukh.xui.ui.format.formatUptime

@Composable
fun NodesScreen(
    vm: NodesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingUpdate by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.transientMessage) {
        state.transientMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.dismissMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            state.error != null && state.items.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(16.dp),
                Alignment.Center,
            ) { Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error) }

            state.items.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.Hub,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    tr("No nodes. Tap + to add a remote panel."),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.id }) { node ->
                    NodeRow(
                        node = node,
                        latestVersion = state.latestVersion,
                        traffic = state.trafficByNode[node.id],
                        updating = node.id in state.updatingIds,
                        onUpdate = { pendingUpdate = node.id },
                        onClick = { vm.openEditEditor(node.id) },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = vm::openCreateEditor,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = tr("Add node")) }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { Snackbar { Text(it.visuals.message) } }
    }

    pendingUpdate?.let { id ->
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
                    if (dev) {
                        Text(
                            tr("Dev builds are unstable."),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.updateNode(id, dev); pendingUpdate = null }) { Text(tr("Update")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUpdate = null }) { Text(tr("Cancel")) }
            },
        )
    }

    // The node editor is rendered as a full-screen overlay by MainScreen
    // (activity window) so its insets/keyboard handling work correctly.
}

@Composable
private fun NodeRow(
    node: Node,
    latestVersion: String,
    traffic: ServerTraffic?,
    updating: Boolean,
    onUpdate: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (node.enable) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(10.dp).background(
                        color = if (node.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        shape = CircleShape,
                    ),
                )
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        node.remark.ifBlank { node.name }.ifBlank { "#${node.id}" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${node.scheme}://${node.address}:${node.port}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (node.online) tr("online") else node.status.ifBlank { tr("offline") },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (node.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            if (node.online) {
                Spacer(Modifier.height(4.dp))
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    "${tr("CPU")}: ${node.cpuPct.formatPercent()}  ·  ${tr("RAM")}: ${node.memPct.formatPercent()}",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (node.latencyMs > 0) {
                    Text("${tr("Ping")}: ${node.latencyMs} ms", style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    "${node.inboundCount} ${tr("inbounds")}  ·  ${node.clientCount} ${tr("clients")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
                if (node.uptimeSecs > 0) {
                    Text("${tr("Uptime")}: ${node.uptimeSecs.formatUptime()}", style = MaterialTheme.typography.labelMedium, color = muted)
                }
                if (node.panelVersion.isNotBlank()) {
                    val outdated = latestVersion.isNotBlank() &&
                        node.panelVersion.removePrefix("v") != latestVersion
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("3x-ui: v${node.panelVersion}", style = MaterialTheme.typography.labelMedium, color = muted)
                        if (outdated) {
                            TextButton(onClick = onUpdate, enabled = !updating) {
                                Text(if (updating) tr("Updating…") else tr("Update"))
                            }
                        }
                    }
                }
            } else if (node.lastError.isNotBlank()) {
                Text(node.lastError, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            // Proxied traffic this month (central counter, shown even if the node
            // is momentarily offline). A trailing "*" flags a non-monthly inbound.
            traffic?.let { t ->
                val flag = if (t.allMonthly) "" else " *"
                Text(
                    "${tr("Traffic this month")}: ${t.bytes.formatBytes()}$flag",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
