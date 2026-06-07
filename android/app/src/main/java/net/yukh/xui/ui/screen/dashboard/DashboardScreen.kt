package net.yukh.xui.ui.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.yukh.xui.data.api.dto.ServerStatus
import net.yukh.xui.ui.format.formatBytes
import net.yukh.xui.ui.format.formatPercent
import net.yukh.xui.ui.format.formatUptime

@Composable
fun DashboardScreen(
    vm: DashboardViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showRestartDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.startPolling()
                Lifecycle.Event.ON_STOP -> vm.stopPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.xrayActionMessage) {
        state.xrayActionMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.dismissActionMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            XrayStatusCard(
                status = state.status,
                actionInFlight = state.xrayActionInFlight,
                onRestartClick = { showRestartDialog = true },
            )

            val status = state.status
            if (status == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.loading) CircularProgressIndicator()
                    else Text("Waiting for first response…")
                }
            } else {
                MetricBarCard(
                    icon = Icons.Outlined.Speed,
                    title = "CPU" + if (status.cpuCores > 0) " · ${status.cpuCores} cores" else "",
                    primaryValue = status.cpu.formatPercent(),
                    progress = (status.cpu / 100.0).toFloat().coerceIn(0f, 1f),
                )
                MetricBarCard(
                    icon = Icons.Outlined.Memory,
                    title = "Memory",
                    primaryValue = status.memPercent.formatPercent(),
                    secondaryValue = "${status.mem.current.formatBytes()} / ${status.mem.total.formatBytes()}",
                    progress = (status.memPercent / 100.0).toFloat().coerceIn(0f, 1f),
                )
                if (status.disk.total > 0) {
                    MetricBarCard(
                        icon = Icons.Outlined.Storage,
                        title = "Disk",
                        primaryValue = status.diskPercent.formatPercent(),
                        secondaryValue = "${status.disk.current.formatBytes()} / ${status.disk.total.formatBytes()}",
                        progress = (status.diskPercent / 100.0).toFloat().coerceIn(0f, 1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricTileCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.People,
                        title = "Online",
                        value = state.onlineCount.toString(),
                    )
                    MetricTileCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.SwapVert,
                        title = "Net ↑ / ↓ per s",
                        value = "${status.netIO.up.formatBytes()} / ${status.netIO.down.formatBytes()}",
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricTileCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Dns,
                        title = "Connections",
                        value = "TCP ${status.tcpCount} · UDP ${status.udpCount}",
                    )
                    MetricTileCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Speed,
                        title = "Load 1·5·15m",
                        value = "%.2f·%.2f·%.2f".format(status.load1, status.load5, status.load15),
                    )
                }

                if (status.uptime > 0 || status.panelVersion.isNotBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (status.uptime > 0) {
                                Text(
                                    "Uptime ${status.uptime.formatUptime()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            val meta = buildList {
                                if (status.panelVersion.isNotBlank()) add("panel ${status.panelVersion}")
                                if (status.xray.version.isNotBlank()) add("xray ${status.xray.version}")
                                if (status.publicIP.ipv4.isNotBlank() && status.publicIP.ipv4 != "N/A") {
                                    add(status.publicIP.ipv4)
                                }
                            }
                            if (meta.isNotEmpty()) {
                                Text(
                                    meta.joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (state.refreshingNow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Refreshing…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { Snackbar { Text(it.visuals.message) } }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Restart Xray?") },
            text = { Text("This briefly drops every active client connection.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    vm.restartXray()
                }) { Text("Restart") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun XrayStatusCard(
    status: ServerStatus?,
    actionInFlight: Boolean,
    onRestartClick: () -> Unit,
) {
    val running = status?.xrayRunning == true
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (running) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Xray", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = when {
                        status == null -> "Status unknown"
                        running -> "Running"
                        else -> "Stopped"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                val err = status?.xray?.errorMsg.orEmpty()
                if (!running && err.isNotBlank()) {
                    Text(
                        err,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            AssistChip(
                onClick = onRestartClick,
                enabled = !actionInFlight,
                label = { Text(if (actionInFlight) "Restarting…" else "Restart") },
                leadingIcon = {
                    if (actionInFlight) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                    }
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun MetricBarCard(
    icon: ImageVector,
    title: String,
    primaryValue: String,
    progress: Float,
    secondaryValue: String? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    "  $title",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(primaryValue, style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            if (secondaryValue != null) {
                Text(
                    secondaryValue,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricTileCard(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    "  $title",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
