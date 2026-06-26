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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import net.yukh.xui.shared.dto.Client
import net.yukh.xui.shared.dto.ClientIpInfo
import net.yukh.xui.shared.dto.ClientModel
import net.yukh.xui.shared.dto.InboundSlim

private const val GBC = 1_073_741_824.0

/** Edit a client's basic fields + inbound membership + view its connection links
 *  + delete. Mirrors the Android client editor. The inbound multi-select shows for
 *  both new and existing clients; on save the caller reconciles the membership
 *  (attach the newly-checked inbounds, detach the unchecked ones). */
@Composable
fun ClientEditorScreen(
    source: Client,
    isNew: Boolean,
    availableInbounds: List<InboundSlim>,
    saving: Boolean,
    error: String?,
    links: List<String>,
    linksLoading: Boolean,
    onShowLinks: () -> Unit,
    ips: List<ClientIpInfo>,
    ipsLoading: Boolean,
    onShowIps: () -> Unit,
    onClearIps: () -> Unit,
    onSave: (ClientModel, List<Int>) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    // Pre-select the client's current inbound membership (empty for a new client).
    var selectedInbounds by remember { mutableStateOf(source.inboundIds.toSet()) }
    var email by remember { mutableStateOf(source.email) }
    var enable by remember { mutableStateOf(source.enable) }
    var totalGb by remember { mutableStateOf(gbStr(source.totalGB)) }
    var limitIp by remember { mutableStateOf(if (source.limitIp > 0) source.limitIp.toString() else "") }
    var reset by remember { mutableStateOf(if (source.reset > 0) source.reset.toString() else "") }
    var tgId by remember { mutableStateOf(if (source.tgId != 0L) source.tgId.toString() else "") }
    var group by remember { mutableStateOf(source.group) }
    var comment by remember { mutableStateOf(source.comment) }

    val canSave = !saving && email.isNotBlank() && (!isNew || selectedInbounds.isNotEmpty())
    fun build(): ClientModel = source.toModel().copy(
        email = email.trim(),
        enable = enable,
        totalGB = totalGb.trim().replace(',', '.').toDoubleOrNull()?.let { (it * GBC).toLong() } ?: 0L,
        limitIp = limitIp.toIntOrNull() ?: 0,
        reset = reset.toIntOrNull() ?: 0,
        tgId = tgId.toLongOrNull() ?: 0L,
        group = group.trim(),
        comment = comment.trim(),
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !saving) { Text(tr("Cancel")) }
            Text(if (isNew) tr("New client") else tr("Edit client"), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onSave(build(), selectedInbounds.toList()) }, enabled = canSave) { Text(if (saving) "…" else tr("Save")) }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)

            CField(email, { email = it }, tr("Email / name"))
            CField(totalGb, { totalGb = it }, tr("Traffic limit (GB, 0 = unlimited)"), KeyboardType.Decimal)
            CField(limitIp, { limitIp = it.filter(Char::isDigit) }, tr("IP limit (0 = unlimited)"), KeyboardType.Number)
            CField(reset, { reset = it.filter(Char::isDigit) }, tr("Traffic reset period (days, 0 = off)"), KeyboardType.Number)
            CField(tgId, { tgId = it.filter(Char::isDigit) }, tr("Telegram ID (optional)"), KeyboardType.Number)
            CField(group, { group = it }, tr("Group (optional)"))
            CField(comment, { comment = it }, tr("Comment (optional)"))
            CToggle(tr("Enabled"), enable) { enable = it }

            // Inbound membership — editable for both new and existing clients.
            Text(tr("Attach to inbounds"), style = MaterialTheme.typography.labelMedium)
            availableInbounds.forEach { ib ->
                val on = ib.id in selectedInbounds
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedInbounds = if (on) selectedInbounds - ib.id else selectedInbounds + ib.id
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = {
                        selectedInbounds = if (it) selectedInbounds + ib.id else selectedInbounds - ib.id
                    })
                    Text(ib.remark.ifBlank { "inbound #${ib.id}" } + "  ·  ${ib.protocol.uppercase()}:${ib.port}")
                }
            }

            if (!isNew) {
                OutlinedButton(onClick = onShowLinks, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Show connection links"))
                }
                if (linksLoading) Text(tr("Loading…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                links.forEach { link ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Image(
                                painter = rememberQrCodePainter(link),
                                contentDescription = null,
                                modifier = Modifier.size(200.dp),
                            )
                            SelectionContainer { Text(link, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                // Per-client IP log (panel v3.4): list the source IPs the panel
                // recorded for this email, with a clear-log action.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onShowIps, modifier = Modifier.weight(1f)) {
                        Text(tr("IP log"))
                    }
                    if (ips.isNotEmpty()) {
                        OutlinedButton(onClick = onClearIps) { Text(tr("Clear")) }
                    }
                }
                if (ipsLoading) Text(tr("Loading…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ips.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ips.forEach { rec ->
                                val suffix = listOfNotNull(
                                    rec.node.ifBlank { null },
                                    rec.time.ifBlank { null },
                                ).joinToString(" · ")
                                Text(
                                    if (suffix.isBlank()) rec.ip else "${rec.ip}  ·  $suffix",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDelete,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(tr("Delete client")) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun gbStr(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes % 1_073_741_824L == 0L -> (bytes / 1_073_741_824L).toString()
    else -> ((bytes * 100 / 1_073_741_824L) / 100.0).toString()
}

@Composable
private fun CField(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text) {
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
private fun CToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
