package net.yukh.xui.ui.screen.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.SystemUpdate
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.format.formatBytes
import net.yukh.xui.ui.format.formatDayMonth
import net.yukh.xui.ui.format.formatPercent
import net.yukh.xui.ui.format.formatUptime

@Composable
fun DashboardScreen(
    vm: DashboardViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showRestartDialog by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var pendingGeoFile by remember { mutableStateOf<String?>(null) }
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
    LaunchedEffect(state.updateMessage) {
        state.updateMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.dismissUpdateMessage()
        }
    }
    LaunchedEffect(state.geoMessage) {
        state.geoMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.dismissGeoMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.pullRefreshing,
            onRefresh = vm::onPullRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
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
                onStart = vm::startXray,
                onStop = { showStopDialog = true },
                onRestart = { showRestartDialog = true },
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
                    else Text(tr("Waiting for first response…"))
                }
            } else {
                MetricBarCard(
                    icon = Icons.Outlined.Speed,
                    title = tr("CPU") + if (status.cpuCores > 0) " · ${status.cpuCores} ${tr("cores")}" else "",
                    primaryValue = status.cpu.formatPercent(),
                    progress = (status.cpu / 100.0).toFloat().coerceIn(0f, 1f),
                    onClick = { vm.openMetricChart(MetricBlock.CPU) },
                )
                MetricBarCard(
                    icon = Icons.Outlined.Memory,
                    title = tr("Memory"),
                    primaryValue = status.memPercent.formatPercent(),
                    secondaryValue = "${status.mem.current.formatBytes()} / ${status.mem.total.formatBytes()}",
                    progress = (status.memPercent / 100.0).toFloat().coerceIn(0f, 1f),
                    onClick = { vm.openMetricChart(MetricBlock.MEMORY) },
                )
                if (status.disk.total > 0) {
                    MetricBarCard(
                        icon = Icons.Outlined.Storage,
                        title = tr("Disk"),
                        primaryValue = status.diskPercent.formatPercent(),
                        secondaryValue = "${status.disk.current.formatBytes()} / ${status.disk.total.formatBytes()}",
                        progress = (status.diskPercent / 100.0).toFloat().coerceIn(0f, 1f),
                        onClick = { vm.openMetricChart(MetricBlock.DISK) },
                    )
                }

                // Proxied (VPN) traffic this month for the main panel's own
                // inbounds (sub-nodes are shown per-node on the Nodes tab).
                state.mainTraffic?.let { t ->
                    MetricTileCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.DataUsage,
                        title = tr("Traffic this month"),
                        value = t.bytes.formatBytes(),
                        caption = if (!t.allMonthly) {
                            tr("not all inbounds reset monthly")
                        } else {
                            t.sinceMillis.formatDayMonth().takeIf { it.isNotBlank() }
                                ?.let { "${tr("since")} $it" }
                        },
                    )
                }

                // Each metric is its own full-width card, in this order:
                // CPU, Memory, (Disk), Traffic, Load, Net, Connections, Online.
                MetricTileCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Speed,
                    title = tr("Load 1·5·15m"),
                    value = "%.2f·%.2f·%.2f".format(status.load1, status.load5, status.load15),
                    onClick = { vm.openMetricChart(MetricBlock.LOAD) },
                )
                MetricTileCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.SwapVert,
                    title = tr("Net ↑ / ↓ per s"),
                    value = "${status.netIO.up.formatBytes()} / ${status.netIO.down.formatBytes()}",
                    onClick = { vm.openMetricChart(MetricBlock.NET) },
                )
                MetricTileCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Dns,
                    title = tr("Connections"),
                    value = "TCP ${status.tcpCount} · UDP ${status.udpCount}",
                    onClick = { vm.openMetricChart(MetricBlock.CONN) },
                )
                MetricTileCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.People,
                    title = tr("Online (tap)"),
                    value = state.onlineCount.toString(),
                    onClick = vm::openOnlineList,
                )

                if (status.uptime > 0 || status.publicIP.ipv4.isNotBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (status.uptime > 0) {
                                Text("${tr("Uptime")} ${status.uptime.formatUptime()}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (status.publicIP.ipv4.isNotBlank() && status.publicIP.ipv4 != "N/A") {
                                Text(
                                    "${tr("IP")} ${status.publicIP.ipv4}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                PanelVersionCard(
                    statusVersion = status.panelVersion,
                    info = state.updateInfo,
                    updating = state.updating,
                    onUpdate = { showUpdateDialog = true },
                )

                GeoDatabasesCard(
                    files = GEO_FILES,
                    updating = state.geoUpdating,
                    onUpdate = { pendingGeoFile = it },
                )
            }

            if (state.refreshingNow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        tr("Refreshing…"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            title = { Text(tr("Restart Xray?")) },
            text = { Text(tr("This briefly drops every active client connection.")) },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    vm.restartXray()
                }) { Text(tr("Restart")) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text(tr("Cancel")) }
            },
        )
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text(tr("Stop Xray?")) },
            text = { Text(tr("This disconnects every active client until you start Xray again.")) },
            confirmButton = {
                TextButton(onClick = {
                    showStopDialog = false
                    vm.stopXray()
                }) { Text(tr("Stop"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text(tr("Cancel")) }
            },
        )
    }

    if (showUpdateDialog) {
        val info = state.updateInfo
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text(tr("Update 3x-ui?")) },
            text = {
                Text(
                    "Update the panel from ${info?.currentVersion.orEmpty()} to " +
                        "${info?.latestVersion.orEmpty()}? The panel restarts during the update.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    vm.updatePanel()
                }) { Text(tr("Update")) }
            },
            dismissButton = { TextButton(onClick = { showUpdateDialog = false }) { Text(tr("Cancel")) } },
        )
    }

    if (state.showOnlineList) {
        OnlineListDialog(
            groups = state.onlineGroups,
            loading = state.onlineLoading,
            onDismiss = vm::closeOnlineList,
        )
    }

    state.metricChart?.let { mc ->
        MetricHistoryDialog(
            state = mc,
            onBucket = vm::setMetricBucket,
            onDismiss = vm::closeMetricChart,
        )
    }

    pendingGeoFile?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingGeoFile = null },
            title = { Text(tr("Update geo database?")) },
            text = {
                Text(
                    "$file\n\n" + tr("Downloads the latest database and restarts Xray, " +
                        "briefly dropping every active connection."),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateGeofile(file)
                    pendingGeoFile = null
                }) { Text(tr("Update")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingGeoFile = null }) { Text(tr("Cancel")) }
            },
        )
    }
}

@Composable
private fun OnlineListDialog(
    groups: List<net.yukh.xui.ui.screen.dashboard.OnlineGroup>,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Online by server")) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                groups.forEach { g ->
                    val header = if (g.isMain) tr("Main server") else g.server
                    Text(
                        "$header (${g.emails.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (g.emails.isEmpty()) {
                        Text(
                            "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    } else {
                        g.emails.forEach { email ->
                            Text(
                                email,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
    )
}

@Composable
private fun PanelVersionCard(
    statusVersion: String,
    info: net.yukh.xui.data.api.dto.PanelUpdateInfo?,
    updating: Boolean,
    onUpdate: () -> Unit,
) {
    val current = info?.currentVersion?.takeIf { it.isNotBlank() } ?: statusVersion
    val updateAvailable = info?.updateAvailable == true
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (updateAvailable) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tr("3x-ui panel"), style = MaterialTheme.typography.labelMedium)
                Text(
                    if (current.isNotBlank()) "v$current" else tr("version unknown"),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (updateAvailable) {
                    Text(
                        "${tr("Update available:")} ${info?.latestVersion.orEmpty()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                } else if (info != null) {
                    Text(
                        tr("Up to date"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (updateAvailable) {
                AssistChip(
                    onClick = onUpdate,
                    enabled = !updating,
                    label = { Text(if (updating) tr("Updating…") else tr("Update")) },
                    leadingIcon = {
                        if (updating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                        }
                    },
                )
            }
        }
    }
}

// The panel's geo-database allowlist (see ServerService.UpdateGeofile). Each can
// be re-downloaded individually; the panel rejects any name outside this set.
private val GEO_FILES = listOf(
    "geoip.dat", "geosite.dat",
    "geoip_RU.dat", "geosite_RU.dat",
    "geoip_IR.dat", "geosite_IR.dat",
)

@Composable
private fun GeoDatabasesCard(
    files: List<String>,
    updating: Set<String>,
    onUpdate: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    "  ${tr("Geo databases")}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            files.forEach { name ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (name in updating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { onUpdate(name) }) { Text(tr("Update")) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XrayStatusCard(
    status: ServerStatus?,
    actionInFlight: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
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
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    val version = status?.xray?.version.orEmpty()
                    Text(
                        tr("Xray") + if (version.isNotBlank()) "  ·  v$version" else "",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = when {
                            status == null -> tr("Status unknown")
                            running -> tr("Running")
                            else -> tr("Stopped")
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    // Xray core uptime (resets on restart) — appStats is the xray
                    // process's own stats, distinct from the server's uptime.
                    val xrayUptime = status?.appStats?.uptime ?: 0
                    if (running && xrayUptime > 0) {
                        Text(
                            "${tr("Xray uptime")} ${xrayUptime.formatUptime()}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    val err = status?.xray?.errorMsg.orEmpty()
                    if (!running && err.isNotBlank()) {
                        Text(
                            err,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                if (actionInFlight) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }

            // running → Restart + Stop; stopped → Start. (Stopping Xray can cut
            // off a panel reverse-proxied through Xray — safe only on a direct
            // connection.) FlowRow wraps the two chips to a centered second line
            // when they don't fit one row (e.g. RU "Перезапустить"+"Остановить").
            if (status != null) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (running) {
                        XrayActionChip(tr("Restart"), Icons.Outlined.RestartAlt, !actionInFlight, onRestart)
                        XrayActionChip(
                            tr("Stop"),
                            Icons.Outlined.Stop,
                            !actionInFlight,
                            onStop,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        XrayActionChip(tr("Start"), Icons.Outlined.PlayArrow, !actionInFlight, onStart)
                    }
                }
            }
        }
    }
}

@Composable
private fun XrayActionChip(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, color = tint) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint) },
        colors = AssistChipDefaults.assistChipColors(containerColor = Color.Transparent),
    )
}

@Composable
private fun MetricBarCard(
    icon: ImageVector,
    title: String,
    primaryValue: String,
    progress: Float,
    secondaryValue: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Card(modifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()) {
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
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
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
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
