package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import net.yukh.xui.shared.dto.NodeModel

/** Full-screen add/edit form for a node, mirroring the Android NodeEditorScreen. */
@Composable
fun NodeEditorScreen(
    initial: NodeModel,
    isNew: Boolean,
    saving: Boolean,
    error: String?,
    inboundCount: Int = 0,
    onSave: (NodeModel) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var remark by remember { mutableStateOf(initial.remark) }
    var scheme by remember { mutableStateOf(initial.scheme.ifBlank { "https" }) }
    var address by remember { mutableStateOf(initial.address) }
    var port by remember { mutableStateOf(if (initial.port > 0) initial.port.toString() else "") }
    var basePath by remember { mutableStateOf(initial.basePath) }
    var apiToken by remember { mutableStateOf(initial.apiToken) }
    var enable by remember { mutableStateOf(initial.enable) }
    var allowPrivate by remember { mutableStateOf(initial.allowPrivateAddress) }
    var tlsMode by remember { mutableStateOf(initial.tlsVerifyMode.ifBlank { "verify" }) }
    var outboundTag by remember { mutableStateOf(initial.outboundTag) }

    // mTLS authenticates the panel→node link with a client cert, so the API
    // token is optional in that mode (matches the Android editor).
    val canSave = !saving && name.isNotBlank() && address.isNotBlank() &&
        (port.toIntOrNull() ?: 0) in 1..65535 && (apiToken.isNotBlank() || tlsMode == "mtls")

    fun build() = initial.copy(
        name = name.trim(), remark = remark.trim(), scheme = scheme, address = address.trim(),
        port = port.toIntOrNull() ?: 443, basePath = basePath.trim().ifBlank { "/" },
        apiToken = apiToken.trim(), enable = enable, allowPrivateAddress = allowPrivate,
        tlsVerifyMode = tlsMode,
        outboundTag = outboundTag.trim(),
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !saving) { Text(tr("Cancel")) }
            Text(if (isNew) tr("Add node") else tr("Edit node"), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onSave(build()) }, enabled = canSave) {
                Text(if (saving) "…" else tr("Save"))
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)

            Field(name, { name = it }, tr("Name"))
            Field(remark, { remark = it }, tr("Remark (optional)"))

            Text(tr("Scheme"), style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = scheme == "https", onClick = { scheme = "https" }); Text("https")
                Spacer(Modifier.width(16.dp))
                RadioButton(selected = scheme == "http", onClick = { scheme = "http" }); Text("http")
            }

            Field(address, { address = it }, tr("Address (IP or domain)"))
            Field(port, { port = it.filter(Char::isDigit) }, tr("Port"), KeyboardType.Number)
            Field(basePath, { basePath = it }, tr("Base path"))
            Field(apiToken, { apiToken = it }, tr("API token"))
            // Route the panel→node API link through this Xray outbound tag
            // (empty = direct). Panel v3.4.0.
            Field(outboundTag, { outboundTag = it }, tr("Route via outbound tag (optional)"))

            ToggleRow(tr("Enabled"), enable) { enable = it }

            Text(tr("TLS verify"), style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("verify", "skip", "pin", "mtls").forEach { mode ->
                    FilterChip(selected = tlsMode == mode, onClick = { tlsMode = mode }, label = { Text(mode) })
                }
            }
            if (tlsMode == "mtls") {
                Text(
                    tr("Mutual TLS: the API token is optional. Register this panel's CA on the node (menu → Node mTLS)."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ToggleRow(tr("Allow private address"), allowPrivate) { allowPrivate = it }

            if (!isNew) {
                Spacer(Modifier.height(8.dp))
                // A node with bound inbounds can't be removed — the panel rejects
                // it, so block the action and say how many must be detached first.
                Button(
                    onClick = onDelete,
                    enabled = !saving && inboundCount == 0,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(tr("Delete node")) }
                if (inboundCount > 0) {
                    Text(
                        "${tr("Detach its inbounds first")} ($inboundCount)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text) {
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
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
