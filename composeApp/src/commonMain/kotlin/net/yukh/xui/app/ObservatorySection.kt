package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.yukh.xui.shared.json.burstObservatoryWithDefaults
import net.yukh.xui.shared.json.hasMixedObservers
import net.yukh.xui.shared.json.jsonGetBool
import net.yukh.xui.shared.json.jsonGetString
import net.yukh.xui.shared.json.jsonGetStrings
import net.yukh.xui.shared.json.observatoryActiveView
import net.yukh.xui.shared.json.observatoryWithDefaults
import net.yukh.xui.shared.json.putBurstPingField
import net.yukh.xui.shared.json.putBurstPingSampling
import net.yukh.xui.shared.json.putObservatoryConcurrency
import net.yukh.xui.shared.json.putObservatoryField

private val HTTP_METHODS = listOf("HEAD", "GET")

/** Observer probe settings under the balancers, like the panel's Observatory tab:
 *  the observers themselves come and go with the balancers (syncObservatories). */
@Composable
fun ObservatorySection(configJson: String, onConfigChange: (String) -> Unit) {
    XraySection(tr("Observatory"))
    val view = observatoryActiveView(configJson)
    if (view == null) {
        XrayHelp(tr("No connection observer is active. One is added automatically when you create a Least Ping or Least Load balancer — or a Random / Round-robin balancer with a fallback — so observer-backed balancers can check outbound health before choosing a target."))
        return
    }
    XrayHelp(tr("Observers are managed automatically from your balancers. Tune how they probe below — the watched outbounds follow your balancer selectors."))
    if (hasMixedObservers(configJson)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(
                tr("This config contains both Observatory and Burst Observatory. Xray uses one global observer, so this mixed legacy state is not supported; saving balancers will normalize it to one observer."),
                Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }

    if (view == "observatory") {
        val obs = observatoryWithDefaults(configJson) ?: return
        fun put(key: String, v: String) = onConfigChange(putObservatoryField(configJson, key, v))
        WatchedOutbounds(jsonGetStrings(obs, listOf("subjectSelector")))
        ObservatoryItem(tr("URL fetched to measure each outbound. Should return HTTP 204.")) {
            XrayField(jsonGetString(obs, listOf("probeURL")), { put("probeURL", it) }, tr("Probe URL"))
        }
        ObservatoryItem(tr("How often to probe each outbound, e.g. 30s, 1m, 2h45m.")) {
            XrayField(jsonGetString(obs, listOf("probeInterval")), { put("probeInterval", it) }, tr("Probe Interval"))
        }
        ObservatoryItem(tr("Probe all watched outbounds at once instead of one-by-one. Faster, but more visible on the network.")) {
            XrayToggle(tr("Concurrent Probing"), jsonGetBool(obs, listOf("enableConcurrency"))) {
                onConfigChange(putObservatoryConcurrency(configJson, it))
            }
        }
    } else {
        val burst = burstObservatoryWithDefaults(configJson) ?: return
        fun ping(key: String) = jsonGetString(burst, listOf("pingConfig", key))
        fun put(key: String, v: String) = onConfigChange(putBurstPingField(configJson, key, v))
        WatchedOutbounds(jsonGetStrings(burst, listOf("subjectSelector")))
        ObservatoryItem(tr("URL fetched to measure each outbound. Should return HTTP 204.")) {
            XrayField(ping("destination"), { put("destination", it) }, tr("Probe Destination"))
        }
        ObservatoryItem(tr("Optional local-network check URL, tried only after the destination fails. Leave empty to skip.")) {
            XrayField(ping("connectivity"), { put("connectivity", it) }, tr("Connectivity Check"))
        }
        ObservatoryItem(tr("Average time between probes per outbound, e.g. 1m. Minimum 10s.")) {
            XrayField(ping("interval"), { put("interval", it) }, tr("Probe Interval"))
        }
        ObservatoryItem(tr("How long to wait for a probe before it counts as failed, e.g. 5s.")) {
            XrayField(ping("timeout"), { put("timeout", it) }, tr("Probe Timeout"))
        }
        // Kept locally so the field can be cleared while typing; only an int ≥ 1 is stored.
        val sampling = ping("sampling")
        var samplingText by remember(sampling) { mutableStateOf(sampling) }
        ObservatoryItem(tr("Number of recent probe results kept to score each outbound.")) {
            OutlinedTextField(
                value = samplingText,
                onValueChange = { t ->
                    val digits = t.filter(Char::isDigit)
                    samplingText = digits
                    digits.toIntOrNull()?.takeIf { it >= 1 }?.let { onConfigChange(putBurstPingSampling(configJson, it)) }
                },
                label = { Text(tr("Sampling Count")) },
                isError = (samplingText.toIntOrNull() ?: 0) < 1,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ObservatoryItem(tr("HTTP method used for probes.")) {
            XrayLabel(tr("HTTP Method"))
            XrayChips(HTTP_METHODS, ping("httpMethod")) { put("httpMethod", it) }
        }
    }
}

@Composable
private fun WatchedOutbounds(tags: List<String>) {
    ObservatoryItem(tr("Outbound tags this observer probes. Managed automatically from your balancers.")) {
        XrayLabel(tr("Watched Outbounds"))
        Text(tags.joinToString(", ").ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ObservatoryItem(help: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content()
        XrayHelp(help)
    }
}
