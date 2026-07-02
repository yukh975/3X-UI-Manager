package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import net.yukh.xui.shared.dto.Client
import net.yukh.xui.shared.dto.ServerStatus
import net.yukh.xui.shared.dto.TrafficSummary

/** Server dashboard: CPU/Memory/Disk bars, Xray state, uptime, versions. */
@Composable
fun DashboardScreen(
    host: String,
    status: ServerStatus?,
    clients: List<Client>,
    onlineCount: Int,
    onlineGroups: List<OnlineGroup>,
    onlineLoading: Boolean,
    onExpandOnline: () -> Unit,
    mainTraffic: TrafficSummary?,
    geoFiles: List<String>,
    geoUpdating: Set<String>,
    geoUpdatingAll: Boolean,
    onGeoUpdate: (String) -> Unit,
    onGeoUpdateAll: () -> Unit,
    xrayRunning: Boolean,
    xrayBusy: Boolean,
    onXrayStart: () -> Unit,
    onXrayStop: () -> Unit,
    onXrayRestart: () -> Unit,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onMetric: (MetricBlock) -> Unit,
    appUpdateVersion: String? = null,
    onAppUpdate: () -> Unit = {},
) {
    var pendingGeo by remember { mutableStateOf<String?>(null) }
    var pendingGeoAll by remember { mutableStateOf(false) }
    var pendingXray by remember { mutableStateOf<XrayAct?>(null) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tr("Dashboard"), style = MaterialTheme.typography.headlineSmall)
                Text(
                    host,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRefresh, enabled = !refreshing) {
                Text(if (refreshing) "…" else tr("Refresh"))
            }
        }

        Spacer(Modifier.height(8.dp))

        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }

        if (status == null) {
            Text(tr("Loading…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            XrayCard(
                version = status.xray.version,
                running = xrayRunning,
                busy = xrayBusy,
                stateText = if (xrayRunning) tr("Running") else status.xray.state.ifBlank { tr("Stopped") },
                onStart = onXrayStart,
                onStop = { pendingXray = XrayAct.Stop },
                onRestart = { pendingXray = XrayAct.Restart },
            )
            // Clients first (mirrors the Android dashboard), then the server metrics.
            if (clients.isNotEmpty()) ClientsCard(clients, onlineCount)
            OnlineCard(onlineCount, onlineGroups, onlineLoading, onExpandOnline)
            BarCard(tr("CPU") + if (status.cpuCores > 0) " · ${status.cpuCores} ${tr("cores")}" else "",
                status.cpu.formatPercent(), (status.cpu / 100.0).toFloat(), onClick = { onMetric(MetricBlock.CPU) })
            BarCard(tr("Memory"), status.memPercent.formatPercent(),
                (status.memPercent / 100.0).toFloat(),
                "${status.mem.current.formatBytes()} / ${status.mem.total.formatBytes()}", onClick = { onMetric(MetricBlock.MEMORY) })
            if (status.disk.total > 0) {
                BarCard(tr("Disk"), status.diskPercent.formatPercent(),
                    (status.diskPercent / 100.0).toFloat(),
                    "${status.disk.current.formatBytes()} / ${status.disk.total.formatBytes()}", onClick = { onMetric(MetricBlock.DISK) })
            }
            ValueCard(tr("Load 1·5·15m"),
                "${oneDp(status.load1)}·${oneDp(status.load5)}·${oneDp(status.load15)}", onClick = { onMetric(MetricBlock.LOAD) })
            ValueCard(tr("Net ↑ / ↓ per s"),
                "${status.netIO.up.formatBytes()} / ${status.netIO.down.formatBytes()}", onClick = { onMetric(MetricBlock.NET) })
            ValueCard(tr("Connections"), "TCP ${status.tcpCount} · UDP ${status.udpCount}", onClick = { onMetric(MetricBlock.CONN) })
            if (status.uptime > 0) ValueCard(tr("Uptime"), status.uptime.formatUptime())
            if (status.panelVersion.isNotBlank()) ValueCard(tr("Panel"), "v${status.panelVersion}")
            AppVersionCard(updateVersion = appUpdateVersion, onUpdate = onAppUpdate)
            mainTraffic?.let { TrafficCard(it) }
            if (geoFiles.isNotEmpty()) GeoCard(
                files = geoFiles,
                updating = geoUpdating,
                updatingAll = geoUpdatingAll,
                onUpdate = { pendingGeo = it },
                onUpdateAll = { pendingGeoAll = true },
            )
        }
    }

    pendingXray?.let { act ->
        AlertDialog(
            onDismissRequest = { pendingXray = null },
            title = { Text(if (act == XrayAct.Stop) tr("Stop Xray?") else tr("Restart Xray?")) },
            text = { Text(tr("This briefly drops every active connection.")) },
            confirmButton = {
                TextButton(onClick = {
                    if (act == XrayAct.Stop) onXrayStop() else onXrayRestart()
                    pendingXray = null
                }) { Text(if (act == XrayAct.Stop) tr("Stop") else tr("Restart")) }
            },
            dismissButton = { TextButton(onClick = { pendingXray = null }) { Text(tr("Cancel")) } },
        )
    }

    if (pendingGeoAll) {
        AlertDialog(
            onDismissRequest = { pendingGeoAll = false },
            title = { Text(tr("Update all geo databases?")) },
            text = { Text(tr("Downloads the latest of every geo database and restarts Xray, briefly dropping every active connection.")) },
            confirmButton = { TextButton(onClick = { onGeoUpdateAll(); pendingGeoAll = false }) { Text(tr("Update all")) } },
            dismissButton = { TextButton(onClick = { pendingGeoAll = false }) { Text(tr("Cancel")) } },
        )
    }

    pendingGeo?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingGeo = null },
            title = { Text(tr("Update geo database?")) },
            text = {
                Text("$file\n\n" + tr("Downloads the latest database and restarts Xray, briefly dropping every active connection."))
            },
            confirmButton = { TextButton(onClick = { onGeoUpdate(file); pendingGeo = null }) { Text(tr("Update")) } },
            dismissButton = { TextButton(onClick = { pendingGeo = null }) { Text(tr("Cancel")) } },
        )
    }
}

@Composable
private fun TrafficCard(t: TrafficSummary) {
    val since = formatDayMonth(t.sinceMillis)
    val caption = when {
        !t.allMonthly -> tr("not all inbounds reset monthly")
        since.isNotEmpty() -> "${tr("since")} $since"
        else -> null
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tr("Traffic this month"), style = MaterialTheme.typography.labelMedium)
            Text(t.bytes.formatBytes(), style = MaterialTheme.typography.titleMedium)
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Collapsed accordion (header only) that expands to the per-file list + an
 *  "Update all" action, like the Android dashboard. */
@Composable
private fun GeoCard(
    files: List<String>,
    updating: Set<String>,
    updatingAll: Boolean,
    onUpdate: (String) -> Unit,
    onUpdateAll: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Geo databases"), style = MaterialTheme.typography.labelMedium)
                Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                files.forEach { name ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (name in updating || updatingAll) {
                            Text("…", style = MaterialTheme.typography.titleMedium)
                        } else {
                            TextButton(onClick = { onUpdate(name) }) { Text(tr("Update")) }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (updatingAll) Text("…", style = MaterialTheme.typography.titleMedium)
                    else TextButton(onClick = onUpdateAll) { Text(tr("Update all")) }
                }
            }
        }
    }
}

/** Client totals at a glance: total / enabled / online / depleted (quota used up). */
@Composable
private fun ClientsCard(clients: List<Client>, onlineCount: Int) {
    val total = clients.size
    val enabled = clients.count { it.enable }
    val depleted = clients.count { it.quota > 0 && (it.up + it.down) >= it.quota }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Clients"), style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CountStat(tr("Total"), total.toString())
                CountStat(tr("Enabled"), enabled.toString())
                CountStat(tr("Online"), onlineCount.toString())
                CountStat(tr("Depleted"), depleted.toString())
            }
        }
    }
}

@Composable
private fun CountStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun oneDp(v: Double): String = ((v * 10).toLong() / 10.0).toString()

private enum class XrayAct { Stop, Restart }

@Composable
private fun XrayCard(
    version: String,
    running: Boolean,
    busy: Boolean,
    stateText: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                tr("Xray") + if (version.isNotBlank()) " · v$version" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stateText,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                if (busy) {
                    Text("…", style = MaterialTheme.typography.titleMedium)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (running) {
                            TextButton(onClick = onStop) { Text(tr("Stop")) }
                            TextButton(onClick = onRestart) { Text(tr("Restart")) }
                        } else {
                            TextButton(onClick = onStart) { Text(tr("Start")) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarCard(title: String, value: String, progress: Float, secondary: String? = null, onClick: (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.labelMedium)
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (secondary != null) {
                Text(secondary, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ValueCard(title: String, value: String, onClick: (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The manager app itself — version + an update hint when the GitLab check has
 *  seen a newer release. Sits right below the panel-version card. */
@Composable
private fun AppVersionCard(updateVersion: String?, onUpdate: () -> Unit) {
    val updateAvailable = updateVersion != null
    Card(
        modifier = Modifier.fillMaxWidth()
            .let { if (updateAvailable) it.clickable(onClick = onUpdate) else it },
        colors = if (updateAvailable) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("3X-UI Manager", style = MaterialTheme.typography.labelMedium)
                if (updateAvailable) {
                    Text(
                        "${tr("Update available:")} v$updateVersion",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Text("v${appVersionName()}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Online clients grouped BY SERVER (main panel + each node), like the Android
 *  "Online by server" dialog. Tap to expand — loads each node's own онлайн. */
@Composable
private fun OnlineCard(count: Int, groups: List<OnlineGroup>, loading: Boolean, onExpand: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            expanded = !expanded
            if (expanded) onExpand()
        },
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr("Online by server"), style = MaterialTheme.typography.labelMedium)
                Text(count.toString(), style = MaterialTheme.typography.titleMedium)
            }
            if (expanded) {
                when {
                    loading && groups.isEmpty() -> Text(
                        tr("Loading…"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    groups.isEmpty() -> Text(
                        tr("Nobody online right now."),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> groups.forEach { g ->
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
                                Text(email, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
