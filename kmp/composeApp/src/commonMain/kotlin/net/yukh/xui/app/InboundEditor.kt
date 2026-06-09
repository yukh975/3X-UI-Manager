package net.yukh.xui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.yukh.xui.shared.dto.InboundModel
import net.yukh.xui.shared.dto.InboundTemplates
import net.yukh.xui.shared.dto.buildInbound
import net.yukh.xui.shared.dto.settingsText
import net.yukh.xui.shared.dto.sniffingText
import net.yukh.xui.shared.dto.streamSettingsText

private const val GB = 1_073_741_824.0

/**
 * Inbound editor — create (isNew) and edit. Basics as structured fields;
 * protocol settings / transport-security / sniffing as editable JSON (mirrors
 * the Android editor's template + JSON approach). [initial] for edit is the
 * FULL inbound from getInbound, so unedited JSON round-trips intact.
 */
@Composable
fun InboundEditorScreen(
    initial: InboundModel,
    isNew: Boolean,
    saving: Boolean,
    error: String?,
    onSave: (InboundModel) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var protocol by remember { mutableStateOf(initial.protocol.ifBlank { "vless" }) }
    var remark by remember { mutableStateOf(initial.remark) }
    var listen by remember { mutableStateOf(initial.listen) }
    var port by remember { mutableStateOf(if (initial.port > 0) initial.port.toString() else "") }
    var enable by remember { mutableStateOf(initial.enable) }
    var trafficReset by remember { mutableStateOf(initial.trafficReset.ifBlank { "never" }) }
    var totalGb by remember { mutableStateOf(gbString(initial.total)) }
    var settingsJson by remember { mutableStateOf(if (isNew) InboundTemplates.settings(protocol) else initial.settingsText()) }
    var streamJson by remember { mutableStateOf(if (isNew) InboundTemplates.streamSettings(protocol) else initial.streamSettingsText()) }
    var sniffingJson by remember { mutableStateOf(if (isNew) InboundTemplates.sniffing() else initial.sniffingText()) }

    val portNum = port.toIntOrNull() ?: 0
    val totalBytes = totalGb.trim().replace(',', '.').toDoubleOrNull()?.let { (it * GB).toLong() } ?: 0L
    val built = buildInbound(
        initial, protocol, remark.trim(), listen.trim(), portNum, totalBytes,
        trafficReset, enable, settingsJson, streamJson, sniffingJson,
    )
    val canSave = !saving && portNum in 1..65535 && built != null

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !saving) { Text(tr("Cancel")) }
            Text(if (isNew) tr("New inbound") else tr("Edit inbound"), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { built?.let(onSave) }, enabled = canSave) {
                Text(if (saving) "…" else tr("Save"))
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)

            if (isNew) {
                Text(tr("Protocol"), style = MaterialTheme.typography.labelMedium)
                Chips(InboundTemplates.PROTOCOLS, protocol) { p ->
                    protocol = p
                    settingsJson = InboundTemplates.settings(p)
                    streamJson = InboundTemplates.streamSettings(p)
                }
            } else {
                Text("${protocol.uppercase()}  ·  #${initial.id}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            EditField(remark, { remark = it }, tr("Remark"))
            EditField(listen, { listen = it }, tr("Listen IP (blank = all)"))
            EditField(port, { port = it.filter(Char::isDigit) }, tr("Port"), KeyboardType.Number)
            EditField(totalGb, { totalGb = it }, tr("Traffic limit (GB, 0 = unlimited)"), KeyboardType.Decimal)

            Text(tr("Traffic reset"), style = MaterialTheme.typography.labelMedium)
            Chips(InboundTemplates.TRAFFIC_RESET, trafficReset) { trafficReset = it }

            EditToggle(tr("Enabled"), enable) { enable = it }

            JsonField(tr("Protocol settings (JSON)"), settingsJson) { settingsJson = it }
            JsonField(tr("Transport / security (JSON)"), streamJson) { streamJson = it }
            JsonField(tr("Sniffing (JSON)"), sniffingJson) { sniffingJson = it }
            if (built == null) {
                Text(tr("One of the JSON blocks is invalid."),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }

            if (!isNew) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDelete,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(tr("Delete inbound")) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun gbString(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes % 1_073_741_824L == 0L -> (bytes / 1_073_741_824L).toString()
    else -> ((bytes * 100 / 1_073_741_824L) / 100.0).toString()
}

@Composable
private fun Chips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt ->
            val on = opt == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    opt,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EditField(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun JsonField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = false,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
    )
}

@Composable
private fun EditToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
