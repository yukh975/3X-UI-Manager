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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.yukh.xui.shared.dto.InboundModel

private const val GB = 1_073_741_824.0

/**
 * Basic inbound editor: remark, listen, port, traffic limit, enable. Protocol,
 * transport/security and expiry are shown read-only for now (the full editor is
 * a later parity step). Because [initial] is the FULL inbound fetched via
 * getInbound, settings/streamSettings/sniffing round-trip untouched on save.
 */
@Composable
fun InboundEditorScreen(
    initial: InboundModel,
    saving: Boolean,
    error: String?,
    onSave: (InboundModel) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var remark by remember { mutableStateOf(initial.remark) }
    var listen by remember { mutableStateOf(initial.listen) }
    var port by remember { mutableStateOf(if (initial.port > 0) initial.port.toString() else "") }
    var enable by remember { mutableStateOf(initial.enable) }
    var totalGb by remember { mutableStateOf(gbString(initial.total)) }

    val canSave = !saving && (port.toIntOrNull() ?: 0) in 1..65535

    fun build(): InboundModel {
        val bytes = totalGb.trim().replace(',', '.').toDoubleOrNull()
            ?.let { (it * GB).toLong() } ?: 0L
        return initial.copy(
            remark = remark.trim(),
            listen = listen.trim(),
            port = port.toIntOrNull() ?: initial.port,
            enable = enable,
            total = bytes,
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !saving) { Text(tr("Cancel")) }
            Text(tr("Edit inbound"), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onSave(build()) }, enabled = canSave) {
                Text(if (saving) "…" else tr("Save"))
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)

            Text(
                "${initial.protocol.uppercase()}  ·  #${initial.id}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            EditField(remark, { remark = it }, tr("Remark"))
            EditField(listen, { listen = it }, tr("Listen IP (blank = all)"))
            EditField(port, { port = it.filter(Char::isDigit) }, tr("Port"), KeyboardType.Number)
            EditField(totalGb, { totalGb = it }, tr("Traffic limit (GB, 0 = unlimited)"), KeyboardType.Decimal)

            EditToggle(tr("Enabled"), enable) { enable = it }

            if (initial.expiryTime > 0L) {
                Text(
                    tr("Expiry is set — edit it in the full editor (coming soon)."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                tr("Protocol, transport and security are edited in the full editor (coming soon)."),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
