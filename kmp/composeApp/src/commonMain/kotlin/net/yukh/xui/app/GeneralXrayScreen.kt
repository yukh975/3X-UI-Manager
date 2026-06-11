package net.yukh.xui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.yukh.xui.shared.json.jsonGetBool
import net.yukh.xui.shared.json.jsonGetString
import net.yukh.xui.shared.json.jsonPutBool
import net.yukh.xui.shared.json.jsonPutString

private val ROUTING_STRATEGY = listOf("AsIs", "IPIfNonMatch", "IPOnDemand")
private val LOG_LEVELS = listOf("none", "debug", "info", "warning", "error")
private val MASK_ADDRESS = listOf("", "quarter", "half", "full")
private val STATS = listOf(
    "statsInboundUplink" to "Inbound uplink stats",
    "statsInboundDownlink" to "Inbound downlink stats",
    "statsOutboundUplink" to "Outbound uplink stats",
    "statsOutboundDownlink" to "Outbound downlink stats",
)

/** Structured General / Logs section of the Xray config. Edits the one config
 *  JSON string via path helpers (siblings preserved); test URL is separate. */
@Composable
fun GeneralXrayScreen(
    configJson: String,
    testUrl: String,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    onConfigChange: (String) -> Unit,
    onTestUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    fun s(vararg p: String) = jsonGetString(configJson, p.toList())
    fun putS(value: String, vararg p: String) = onConfigChange(jsonPutString(configJson, p.toList(), value))

    Column(Modifier.fillMaxSize()) {
        XrayEditHeader(tr("General / Logs"), saving, onCancel, onSave, canSave = !loading)
        if (error != null) Text(error, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            XraySection(tr("General"))
            XrayLabel(tr("Routing strategy"))
            XrayChips(ROUTING_STRATEGY, s("routing", "domainStrategy").ifBlank { "AsIs" }) { putS(it, "routing", "domainStrategy") }
            XrayField(testUrl, onTestUrlChange, tr("Outbound test URL"))

            XraySection(tr("Logs"))
            XrayLabel(tr("Log level"))
            XrayChips(LOG_LEVELS, s("log", "loglevel").ifBlank { "warning" }) { putS(it, "log", "loglevel") }
            XrayField(s("log", "access"), { putS(it, "log", "access") }, tr("Access log (path, empty = off)"))
            XrayField(s("log", "error"), { putS(it, "log", "error") }, tr("Error log (path, empty = off)"))
            XrayLabel(tr("Mask address"))
            XrayChips(MASK_ADDRESS, s("log", "maskAddress")) { putS(it, "log", "maskAddress") }
            XrayToggle(tr("DNS log"), jsonGetBool(configJson, listOf("log", "dnsLog"))) {
                onConfigChange(jsonPutBool(configJson, listOf("log", "dnsLog"), it))
            }

            XraySection(tr("Statistics"))
            STATS.forEach { (key, label) ->
                XrayToggle(tr(label), jsonGetBool(configJson, listOf("policy", "system", key))) {
                    onConfigChange(jsonPutBool(configJson, listOf("policy", "system", key), it))
                }
            }
        }
    }
}

// ---- Shared little widgets for the structured Xray section editors ----------

@Composable
fun XrayEditHeader(title: String, saving: Boolean, onCancel: () -> Unit, onSave: () -> Unit, canSave: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel, enabled = !saving) { Text(tr("Cancel")) }
        Text(title, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onSave, enabled = !saving && canSave) { Text(if (saving) "…" else tr("Save")) }
    }
}

@Composable
fun XraySection(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun XrayLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun XrayField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
fun XrayToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun XrayChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val on = opt == selected
            Box(
                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(opt) }.padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    opt.ifBlank { "—" },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
