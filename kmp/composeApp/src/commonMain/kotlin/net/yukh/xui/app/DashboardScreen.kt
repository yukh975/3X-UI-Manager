package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.yukh.xui.shared.dto.ServerStatus

/** Server dashboard: CPU/Memory/Disk bars, Xray state, uptime, versions. */
@Composable
fun DashboardScreen(
    host: String,
    status: ServerStatus?,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Dashboard", style = MaterialTheme.typography.headlineSmall)
                Text(
                    host,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRefresh, enabled = !refreshing) {
                Text(if (refreshing) "…" else "Refresh")
            }
            TextButton(onClick = onDisconnect) { Text("Disconnect") }
        }

        Spacer(Modifier.height(8.dp))

        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }

        if (status == null) {
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            XrayCard(status)
            BarCard("CPU" + if (status.cpuCores > 0) " · ${status.cpuCores} cores" else "",
                status.cpu.formatPercent(), (status.cpu / 100.0).toFloat())
            BarCard("Memory", status.memPercent.formatPercent(),
                (status.memPercent / 100.0).toFloat(),
                "${status.mem.current.formatBytes()} / ${status.mem.total.formatBytes()}")
            if (status.disk.total > 0) {
                BarCard("Disk", status.diskPercent.formatPercent(),
                    (status.diskPercent / 100.0).toFloat(),
                    "${status.disk.current.formatBytes()} / ${status.disk.total.formatBytes()}")
            }
            ValueCard("Load 1·5·15m",
                "${oneDp(status.load1)}·${oneDp(status.load5)}·${oneDp(status.load15)}")
            ValueCard("Net ↑ / ↓ per s",
                "${status.netIO.up.formatBytes()} / ${status.netIO.down.formatBytes()}")
            ValueCard("Connections", "TCP ${status.tcpCount} · UDP ${status.udpCount}")
            if (status.uptime > 0) ValueCard("Uptime", status.uptime.formatUptime())
            if (status.panelVersion.isNotBlank()) ValueCard("Panel", "v${status.panelVersion}")
        }
    }
}

private fun oneDp(v: Double): String = ((v * 10).toLong() / 10.0).toString()

@Composable
private fun XrayCard(status: ServerStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Xray" + if (status.xray.version.isNotBlank()) " · v${status.xray.version}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (status.xrayRunning) "Running" else status.xray.state.ifBlank { "Stopped" },
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
