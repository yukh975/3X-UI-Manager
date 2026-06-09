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
import net.yukh.xui.shared.dto.ServerStatus
import net.yukh.xui.shared.dto.TrafficSummary

/** Server dashboard: CPU/Memory/Disk bars, Xray state, uptime, versions. */
@Composable
fun DashboardScreen(
    host: String,
    status: ServerStatus?,
    onlineCount: Int,
    onlineGroups: List<OnlineGroup>,
    onlineLoading: Boolean,
    onExpandOnline: () -> Unit,
    mainTraffic: TrafficSummary?,
    geoFiles: List<String>,
    geoUpdating: Set<String>,
    onGeoUpdate: (String) -> Unit,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var pendingGeo by remember { mutableStateOf<String?>(null) }
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
            TextButton(onClick = onDisconnect) { Text(tr("Disconnect")) }
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
            XrayCard(status)
            BarCard(tr("CPU") + if (status.cpuCores > 0) " · ${status.cpuCores} ${tr("cores")}" else "",
                status.cpu.formatPercent(), (status.cpu / 100.0).toFloat())
            BarCard(tr("Memory"), status.memPercent.formatPercent(),
                (status.memPercent / 100.0).toFloat(),
                "${status.mem.current.formatBytes()} / ${status.mem.total.formatBytes()}")
            if (status.disk.total > 0) {
                BarCard(tr("Disk"), status.diskPercent.formatPercent(),
                    (status.diskPercent / 100.0).toFloat(),
                    "${status.disk.current.formatBytes()} / ${status.disk.total.formatBytes()}")
            }
            ValueCard(tr("Load 1·5·15m"),
                "${oneDp(status.load1)}·${oneDp(status.load5)}·${oneDp(status.load15)}")
            ValueCard(tr("Net ↑ / ↓ per s"),
                "${status.netIO.up.formatBytes()} / ${status.netIO.down.formatBytes()}")
            ValueCard(tr("Connections"), "TCP ${status.tcpCount} · UDP ${status.udpCount}")
            OnlineCard(onlineCount, onlineGroups, onlineLoading, onExpandOnline)
            if (status.uptime > 0) ValueCard(tr("Uptime"), status.uptime.formatUptime())
            if (status.panelVersion.isNotBlank()) ValueCard(tr("Panel"), "v${status.panelVersion}")
            mainTraffic?.let { TrafficCard(it) }
            if (geoFiles.isNotEmpty()) GeoCard(geoFiles, geoUpdating, onUpdate = { pendingGeo = it })
        }
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

@Composable
private fun GeoCard(files: List<String>, updating: Set<String>, onUpdate: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tr("Geo databases"), style = MaterialTheme.typography.labelMedium)
            files.forEach { name ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (name in updating) {
                        Text("…", style = MaterialTheme.typography.titleMedium)
                    } else {
                        TextButton(onClick = { onUpdate(name) }) { Text(tr("Update")) }
                    }
                }
            }
        }
    }
}

private fun oneDp(v: Double): String = ((v * 10).toLong() / 10.0).toString()

@Composable
private fun XrayCard(status: ServerStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                tr("Xray") + if (status.xray.version.isNotBlank()) " · v${status.xray.version}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (status.xrayRunning) tr("Running") else status.xray.state.ifBlank { tr("Stopped") },
                style = MaterialTheme.typography.titleMedium,
                color = if (status.xrayRunning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun BarCard(title: String, value: String, progress: Float, secondary: String? = null) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
private fun ValueCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
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
