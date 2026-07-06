package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * "Panel alerts" settings card, shared by the More tab and the pre-sign-in
 * settings screen. Mirrors the Android Settings section: master switch +
 * expiry-days / traffic-% thresholds. Turning it on asks for notification
 * permission, queues the background refresh and runs one immediate check.
 */
@Composable
fun PanelAlertsCard() {
    val store = remember { SessionStore() }
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(store.alertsEnabled()) }
    var port by remember { mutableStateOf(store.alertPanelPort().toString()) }
    var days by remember { mutableStateOf(store.alertExpiryDays().toString()) }
    var pct by remember { mutableStateOf(store.alertTrafficPct().toString()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Panel alerts"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("Client expiry, traffic limits, inbounds & node status"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        enabled = on
                        store.setAlertsEnabled(on)
                        if (on) {
                            requestNotificationPermission()
                            scheduleAlertsRefresh()
                            // Immediate first pass — instant feedback.
                            scope.launch { runCatching { AlertsCheck.run(store) } }
                        } else {
                            cancelAlertsRefresh()
                        }
                    },
                )
            }
            if (enabled) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { raw ->
                        val t = raw.filter(Char::isDigit).take(5)
                        port = t
                        t.toIntOrNull()?.takeIf { it in 1..65535 }?.let(store::setAlertPanelPort)
                    },
                    label = { Text(tr("Panel port")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    tr("This port on the panel host is probed for reachability (default 443). Per-inbound ports are monitored separately, from each inbound's editor."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = days,
                        onValueChange = { raw ->
                            val t = raw.filter(Char::isDigit).take(3)
                            days = t
                            t.toIntOrNull()?.takeIf { it in 1..365 }?.let(store::setAlertExpiryDays)
                        },
                        label = { Text(tr("Days before expiry")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = pct,
                        onValueChange = { raw ->
                            val t = raw.filter(Char::isDigit).take(3)
                            pct = t
                            t.toIntOrNull()?.takeIf { it in 1..100 }?.let(store::setAlertTrafficPct)
                        },
                        label = { Text(tr("Traffic threshold (%)")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    tr("Checked when the app opens and opportunistically in the background."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
