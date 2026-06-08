package net.yukh.xui.ui.screen.nodes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import net.yukh.xui.ui.components.AdjustResizeDialogWindow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.yukh.xui.data.api.dto.Node
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.format.formatPercent
import net.yukh.xui.ui.format.formatUptime

@Composable
fun NodesScreen(
    vm: NodesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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

            state.items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(tr("No nodes. Tap + to add a remote panel."))
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.id }) { node ->
                    NodeRow(node = node, onClick = { vm.openEditEditor(node.id) })
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

    state.editor?.let { editor ->
        Dialog(
            onDismissRequest = vm::closeEditor,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AdjustResizeDialogWindow()
            NodeEditorScreen(
                state = editor,
                onName = vm::setName,
                onRemark = vm::setRemark,
                onScheme = vm::setScheme,
                onAddress = vm::setAddress,
                onPort = vm::setPort,
                onBasePath = vm::setBasePath,
                onApiToken = vm::setApiToken,
                onEnable = vm::setEnable,
                onAllowPrivate = vm::setAllowPrivate,
                onTlsVerifyMode = vm::setTlsVerifyMode,
                onSave = vm::saveEditor,
                onDelete = { vm.deleteNode(editor.id) },
                onClose = vm::closeEditor,
            )
        }
    }
}

@Composable
private fun NodeRow(node: Node, onClick: () -> Unit) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("CPU ") + "${node.cpuPct.formatPercent()}", style = MaterialTheme.typography.labelMedium)
                    Text(tr("RAM ") + "${node.memPct.formatPercent()}", style = MaterialTheme.typography.labelMedium)
                    if (node.latencyMs > 0) Text("${node.latencyMs}ms", style = MaterialTheme.typography.labelMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${node.inboundCount} ${tr("inbounds")} · ${node.clientCount} ${tr("clients")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (node.uptimeSecs > 0) Text(tr("up ") + "${node.uptimeSecs.formatUptime()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (node.lastError.isNotBlank()) {
                Text(node.lastError, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
