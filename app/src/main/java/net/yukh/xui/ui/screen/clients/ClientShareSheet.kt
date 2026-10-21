package net.yukh.xui.ui.screen.clients

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.yukh.xui.data.api.dto.HAPP_SOURCE_TOO_LONG
import net.yukh.xui.data.api.dto.QR_MAX_BYTES
import net.yukh.xui.data.api.dto.SubInfo
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.qr.qrImageBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientShareSheet(
    email: String,
    links: List<String>,
    loading: Boolean,
    error: String?,
    subUrl: String?,
    jsonSubUrl: String?,
    subChecked: Boolean,
    subInfo: SubInfo?,
    happLinkEnabled: Boolean?,
    happLink: String?,
    happLinkLoading: Boolean,
    happLinkError: String?,
    onGenerateHappLink: () -> Unit,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onIpLog: () -> Unit,
    onResetTraffic: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        // The whole content scrolls — with several connections the column can
        // exceed the sheet height, and without this the action buttons at the
        // bottom were getting squeezed to a thin, unlabelled, untappable strip.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                email,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            // --- Subscription -------------------------------------------------
            ExpandableSection(
                title = tr("Subscription"),
                subtitle = tr("Auto-updating link for all configs"),
                initiallyExpanded = true,
            ) {
                when {
                    !subChecked -> LoadingBlock()
                    subUrl != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QrAndLink(content = subUrl, context = context)
                        if (subInfo != null) SubStatusRow(subInfo)
                        if (jsonSubUrl != null) JsonSubBlock(jsonSubUrl, context)
                        if (happLinkEnabled != null) {
                            HappLinkBlock(
                                enabled = happLinkEnabled,
                                link = happLink,
                                loading = happLinkLoading,
                                error = happLinkError,
                                onGenerate = onGenerateHappLink,
                                context = context,
                            )
                        }
                    }
                    else -> Text(
                        tr(
                            "No subscription URL. On panel v3.3.0+ the app reads the " +
                                "base automatically (token or login). Otherwise set the " +
                                "\"Subscription base URL\" on the connect screen " +
                                "(e.g. https://host:2096/sub/).",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // --- Connections --------------------------------------------------
            ExpandableSection(
                title = tr("Connections") + if (links.isNotEmpty()) " (${links.size})" else "",
                subtitle = tr("Individual server links"),
                initiallyExpanded = false,
            ) {
                when {
                    loading -> LoadingBlock()
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                    links.isEmpty() -> Text(
                        tr("No connection links for this client."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        links.forEachIndexed { index, link ->
                            ConnectionItem(
                                label = connectionLabel(link, index),
                                link = link,
                                context = context,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Text("  " + tr("Edit"))
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text("  " + tr("Delete"))
                }
            }
            OutlinedButton(onClick = onIpLog, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.History, contentDescription = null)
                Text("  " + tr("IP log"))
            }
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                Text("  " + tr("Reset traffic"))
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("Close")) }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tr("Reset this client's traffic?")) },
            text = {
                Text("$email\n\n" + tr("Used traffic starts from zero, and a client its quota switched off is enabled again."))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onResetTraffic()
                }) { Text(tr("Reset traffic"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(tr("Cancel")) }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Delete client?")) },
            text = { Text("$email ${tr("will be removed from every attached inbound.")}") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel")) }
            },
        )
    }
}

/** Derive a human label from a connection URI: scheme + #remark fragment. */
private fun connectionLabel(link: String, index: Int): String {
    val scheme = link.substringBefore("://", "").uppercase().ifBlank { "Link" }
    val remark = link.substringAfter("#", "").substringBefore("\n").trim()
    val decoded = runCatching { java.net.URLDecoder.decode(remark, "UTF-8") }.getOrDefault(remark)
    return if (decoded.isNotBlank()) "$scheme · $decoded" else "$scheme · #${index + 1}"
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
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
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) tr("Collapse") else tr("Expand"),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Box(modifier = Modifier.padding(top = 10.dp)) { content() }
            }
        }
    }
}

/** A single connection row that expands to show its own QR + link actions. */
@Composable
private fun ConnectionItem(
    label: String,
    link: String,
    context: Context,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Box(modifier = Modifier.padding(top = 8.dp)) {
                    QrAndLink(content = link, context = context)
                }
            }
        }
    }
}

@Composable
private fun QrAndLink(content: String, context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QrCard(content = content)
        LinkRow(content = content, context = context)
    }
}

/** The link text with copy and share buttons. */
@Composable
private fun LinkRow(content: String, context: Context) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                content,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("link", content))
            }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = tr("Copy"))
            }
            TextButton(onClick = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share"))
            }) {
                Icon(Icons.Outlined.Share, contentDescription = tr("Share"))
            }
        }
    }
}

@Composable
private fun QrCard(content: String) {
    // Post-quantum keys fit a QR only at a density phones can't scan; the panel hides it too.
    if (isPostQuantumLink(content)) {
        Text(
            tr("This link carries post-quantum keys and is too dense for a QR code — copy or share it instead."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    // Data past the QR capacity makes the encoder throw; say so instead of crashing the sheet.
    val bitmap = remember(content) { runCatching { qrImageBitmap(content, 768) }.getOrNull() }
    if (bitmap == null) {
        Text(
            tr("Too long for a QR code — copy or share the link instead."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = tr("QR code"),
            modifier = Modifier.size(220.dp),
        )
    }
}

/** The JSON subscription (Xray JSON configs); MASQUE is only offered there (panel 3.10.0). */
@Composable
private fun JsonSubBlock(url: String, context: Context) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider()
        Text(tr("JSON subscription"), style = MaterialTheme.typography.titleSmall)
        Text(
            tr("Xray JSON configs. MASQUE connections are only in this one."),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        QrAndLink(content = url, context = context)
    }
}

/** Encrypted Happ link for the subscription (panel v3.8.0). The panel builds it on
 *  demand; the switch that allows it lives in the panel's subscription settings. */
@Composable
private fun HappLinkBlock(
    enabled: Boolean,
    link: String?,
    loading: Boolean,
    error: String?,
    onGenerate: () -> Unit,
    context: Context,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider()
        Text(tr("Encrypted Happ link"), style = MaterialTheme.typography.titleSmall)
        when {
            !enabled -> Text(
                tr("Turned off on the panel. Enable \"Encrypted subscription links\" in the panel's settings: Subscription → Happ → Subscription links."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            loading -> LoadingBlock()
            link != null -> {
                // Past what a QR code can hold the link is still valid — offer copy/share only.
                if (link.encodeToByteArray().size <= QR_MAX_BYTES) {
                    QrAndLink(content = link, context = context)
                } else {
                    Text(
                        tr("Too long for a QR code — copy or share the link instead."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinkRow(content = link, context = context)
                }
                Text(
                    tr("Only the Happ app opens this link, but anyone who has it may still recover or share the subscription URL."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onGenerate) { Text(tr("Regenerate")) }
            }
            else -> {
                if (error != null) {
                    Text(
                        if (error == HAPP_SOURCE_TOO_LONG) tr("The subscription URL is longer than the panel allows for Happ links (8192 bytes).")
                        else "${tr("Couldn't create the Happ link")}: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(onClick = onGenerate) {
                    Text(if (error != null) tr("Retry") else tr("Create encrypted link"))
                }
            }
        }
    }
}

/** Customer's-eye subscription status from the sub server's `?format=info`
 *  (panel v3.6.0+): the live online state + usage the subscriber's own app sees. */
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
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (info.isOnline) Color(0xFF34C759)
                        else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    ),
            )
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

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}
