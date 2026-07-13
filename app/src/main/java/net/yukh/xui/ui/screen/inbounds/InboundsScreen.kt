package net.yukh.xui.ui.screen.inbounds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.i18n.LocalAppLanguage
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.format.formatBytes
import net.yukh.xui.ui.format.formatExpiry
import net.yukh.xui.ui.format.formatSpeed

@Composable
fun InboundsScreen(
    vm: InboundsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.load() }

    // Live-speed auto-refresh while the Inbounds tab is on-screen.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> vm.startPolling()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> vm.stopPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); vm.stopPolling() }
    }

    LaunchedEffect(state.transientMessage) {
        state.transientMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.dismissMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null && state.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            state.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text(tr("No inbounds yet.")) }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.id }) { inbound ->
                    InboundRow(
                        inbound = inbound,
                        speed = state.speedByInbound[inbound.id],
                        toggling = inbound.id in state.toggleInFlight,
                        onToggle = { vm.toggle(inbound.id, !inbound.enable) },
                        onClick = { vm.openEditEditor(inbound.id) },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = vm::openCreateEditor,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = tr("Add inbound"))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { Snackbar { Text(it.visuals.message) } }
    }

    // The inbound editor is rendered as a full-screen overlay by MainScreen
    // (activity window) so its insets/keyboard handling work correctly.
}

@Composable
private fun InboundRow(
    inbound: InboundSlim,
    speed: Pair<Long, Long>?,
    toggling: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val totalUsed = inbound.up + inbound.down
    val quotaProgress = if (inbound.total > 0) {
        (totalUsed.toFloat() / inbound.total.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (inbound.enable) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = inbound.remark.ifBlank { "inbound #${inbound.id}" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = listOf(
                            inbound.protocol.uppercase().ifBlank { "?" },
                            inbound.listen.ifBlank { "*" } + ":" + inbound.port,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (toggling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Switch(checked = inbound.enable, onCheckedChange = { onToggle() })
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "↑ ${inbound.up.formatBytes()}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "↓ ${inbound.down.formatBytes()}",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (inbound.total > 0) {
                    Text(
                        tr("of ") + "${inbound.total.formatBytes()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (speed != null && (speed.first > 0 || speed.second > 0)) {
                Text(
                    "↑ ${formatSpeed(speed.first, net.yukh.xui.ui.format.LocalSpeedInBits.current)}   ↓ ${formatSpeed(speed.second, net.yukh.xui.ui.format.LocalSpeedInBits.current)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (inbound.total > 0) {
                LinearProgressIndicator(
                    progress = { quotaProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${tr("Expires")}: ${inbound.expiryTime.formatExpiry(LocalAppLanguage.current)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text("${inbound.clientStats.size} ${tr("clients")}")
                    },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }
    }
}
