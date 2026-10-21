package net.yukh.xui.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.QrCodePainter
import io.github.alexzhirkevich.qrose.options.QrOptions
import net.yukh.xui.shared.dto.Client
import net.yukh.xui.shared.dto.ClientHwid
import net.yukh.xui.shared.dto.ClientIpInfo
import net.yukh.xui.shared.dto.HAPP_SOURCE_TOO_LONG
import net.yukh.xui.shared.dto.QR_MAX_BYTES
import net.yukh.xui.shared.dto.SubInfo
import net.yukh.xui.shared.dto.connectionLabel
import net.yukh.xui.shared.dto.isPostQuantumLink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientCardSheet(
    client: Client,
    online: Boolean,
    links: List<String>,
    linksLoading: Boolean,
    linksError: String?,
    subChecked: Boolean,
    subUrl: String?,
    jsonSubUrl: String?,
    subInfo: SubInfo?,
    // Null until the panel's switch is read (panel v3.8.0): the block isn't offered before.
    happLinkEnabled: Boolean?,
    happLink: String?,
    happLinkLoading: Boolean,
    happLinkError: String?,
    onGenerateHappLink: () -> Unit,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onResetTraffic: () -> Unit,
    onIpLog: () -> Unit,
    onDevices: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClientCardHeader(client, online)
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)

            ExpandableSection(
                title = tr("Subscription"),
                subtitle = tr("Auto-updating link for all configs"),
                initiallyExpanded = true,
            ) {
                when {
                    !subChecked -> LoadingBlock()
                    subUrl != null -> Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QrAndLink(subUrl)
                        subInfo?.let { SubStatusRow(it) }
                        jsonSubUrl?.let { JsonSubBlock(it) }
                        if (happLinkEnabled != null) {
                            HappLinkBlock(
                                enabled = happLinkEnabled,
                                link = happLink,
                                loading = happLinkLoading,
                                error = happLinkError,
                                onGenerate = onGenerateHappLink,
                            )
                        }
                    }
                    else -> Text(
                        tr("No subscription URL: the subscription service is off on the panel, or its settings couldn't be read."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ExpandableSection(
                title = tr("Connections") + if (links.isNotEmpty()) " (${links.size})" else "",
                subtitle = tr("Individual server links"),
                initiallyExpanded = false,
            ) {
                when {
                    linksLoading -> LoadingBlock()
                    linksError != null -> Text(linksError, color = MaterialTheme.colorScheme.error)
                    links.isEmpty() -> Text(
                        tr("No connection links for this client."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        links.forEachIndexed { index, link ->
                            ConnectionItem(label = connectionLabel(link, index), link = link)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit, enabled = !busy, modifier = Modifier.weight(1f)) { Text(tr("Edit")) }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(tr("Delete")) }
            }
            OutlinedButton(onClick = onIpLog, modifier = Modifier.fillMaxWidth()) { Text(tr("IP log")) }
            OutlinedButton(onClick = onDevices, modifier = Modifier.fillMaxWidth()) { Text(tr("Devices")) }
            OutlinedButton(onClick = { confirmReset = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Reset traffic"))
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(tr("Close")) }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tr("Reset this client's traffic?")) },
            text = { Text("${client.email}\n\n${tr("Used traffic starts from zero, and a client its quota switched off is enabled again.")}") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onResetTraffic() }) {
                    Text(tr("Reset traffic"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr("Cancel")) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Delete client?")) },
            text = { Text("${client.email} ${tr("will be removed from every attached inbound.")}") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel")) } },
        )
    }
}

@Composable
fun ClientIpLogDialog(
    email: String,
    ips: List<ClientIpInfo>,
    loading: Boolean,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("IP log") + " · " + email) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    ips.isEmpty() -> {
                        Text(tr("No IPs logged."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            tr("The panel keeps an address for 30 minutes after the last connection, so this stays empty for anyone who hasn't connected recently."),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> ips.forEach { rec ->
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
        },
        confirmButton = {
            TextButton(onClick = onClear, enabled = ips.isNotEmpty() && !loading) { Text(tr("Clear")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
    )
}

@Composable
fun ClientDevicesDialog(
    email: String,
    hwids: List<ClientHwid>,
    loading: Boolean,
    unsupported: Boolean,
    onRemove: (ClientHwid) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Devices") + " · " + email) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    unsupported -> Text(
                        tr("Your panel version doesn't support this yet. Update the panel to the latest version to use it."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    hwids.isEmpty() -> Text(
                        tr("No devices registered."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> hwids.forEach { d ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.label.ifBlank { "#" + d.id }, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    tr("Last seen") + ": " + formatDayMonth(d.lastSeen),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (d.fingerprint.isNotBlank()) {
                                    Text(
                                        tr("Device fingerprint") + ": " + d.fingerprint,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            TextButton(onClick = { onRemove(d) }) { Text(tr("Remove")) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClear, enabled = hwids.isNotEmpty()) { Text(tr("Clear")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
    )
}

@Composable
private fun ClientCardHeader(client: Client, online: Boolean) {
    Column(modifier = Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            client.email.ifBlank { "#${client.id}" },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OnlineDot(online)
            Text(if (online) tr("Online") else tr("Offline"), style = MaterialTheme.typography.bodyMedium)
        }
        val quota = if (client.quota > 0) {
            "  ·  ${(client.up + client.down).formatBytes()} ${tr("of")} ${client.quota.formatBytes()}"
        } else {
            ""
        }
        Text(
            "↑ ${client.up.formatBytes()}  ↓ ${client.down.formatBytes()}$quota",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    subtitle: String,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(if (expanded) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Box(modifier = Modifier.padding(top = 10.dp)) { content() }
            }
        }
    }
}

@Composable
private fun ConnectionItem(label: String, link: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(if (expanded) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { QrAndLink(link) }
            }
        }
    }
}

@Composable
private fun QrAndLink(content: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QrImage(content)
        SelectionContainer { Text(content, style = MaterialTheme.typography.bodySmall) }
        LinkActions(content)
    }
}

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun OnlineDot(online: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(
                color = if (online) Color(0xFF34C759) else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            ),
    )
}

@Composable
private fun LinkActions(content: String) {
    val clipboard = LocalClipboardManager.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(content)) }) {
            Text(tr("Copy"))
        }
        OutlinedButton(onClick = { platformShareText(content) }) {
            Text(tr("Share"))
        }
    }
}

/** qrose encodes in the painter's constructor and throws past a QR code's capacity,
 *  so the painter is built inside a catch and a link that doesn't fit gets a note. */
@Composable
private fun QrImage(content: String) {
    // Post-quantum keys fit a QR only at a density phones can't scan; the panel hides it too.
    if (isPostQuantumLink(content)) {
        Text(
            tr("This link carries post-quantum keys and is too dense for a QR code — copy or share it instead."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val painter = remember(content) { runCatching { QrCodePainter(content, QrOptions {}) }.getOrNull() }
    if (painter != null) {
        Image(painter = painter, contentDescription = null, modifier = Modifier.size(200.dp))
    } else {
        QrTooLongNote()
    }
}

@Composable
private fun QrTooLongNote() {
    Text(
        tr("Too long for a QR code — copy or share the link instead."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The JSON subscription (Xray JSON configs); MASQUE is only offered there (panel 3.10.0). */
@Composable
private fun JsonSubBlock(url: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        Text(tr("JSON subscription"), style = MaterialTheme.typography.titleSmall)
        Text(
            tr("Xray JSON configs. MASQUE connections are only in this one."),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        QrAndLink(url)
    }
}

/** Encrypted Happ link (panel v3.8.0), built on demand; the switch allowing it is in the
 *  panel's subscription settings. */
@Composable
private fun HappLinkBlock(
    enabled: Boolean,
    link: String?,
    loading: Boolean,
    error: String?,
    onGenerate: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        Text(tr("Encrypted Happ link"), style = MaterialTheme.typography.titleSmall)
        val errorText = error?.let {
            if (it == HAPP_SOURCE_TOO_LONG) tr("The subscription URL is longer than the panel allows for Happ links (8192 bytes).")
            else "${tr("Couldn't create the Happ link")}: $it"
        }
        when {
            !enabled -> Text(
                tr("Turned off on the panel. Enable \"Encrypted subscription links\" in the panel's settings: Subscription → Happ → Subscription links."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            link != null -> {
                // The link stays valid at any length; past QR level M's capacity it is offered
                // for copy/share only, the same as when the encoder refuses it.
                if (link.encodeToByteArray().size <= QR_MAX_BYTES) QrImage(link) else QrTooLongNote()
                SelectionContainer {
                    Text(link, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                LinkActions(link)
                Text(
                    tr("Only the Happ app opens this link, but anyone who has it may still recover or share the subscription URL."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // A failed regenerate keeps showing the previous link, so name the failure too.
                errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onGenerate) { Text(tr("Regenerate")) }
            }
            else -> {
                errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = onGenerate) {
                    Text(if (error != null) tr("Retry") else tr("Create encrypted link"))
                }
            }
        }
    }
}

/** What the subscriber's own client app shows when it refreshes the link. */
@Composable
private fun SubStatusRow(info: SubInfo) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            tr("As the subscriber sees it"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OnlineDot(info.isOnline)
            Text(
                if (info.isOnline) tr("Online") else tr("Offline"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val usage = listOfNotNull(
            info.used.takeIf { it.isNotBlank() },
            info.total.takeIf { it.isNotBlank() }?.let { "${tr("of")} $it" },
        ).joinToString(" ")
        if (usage.isNotBlank()) {
            Text(
                usage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
