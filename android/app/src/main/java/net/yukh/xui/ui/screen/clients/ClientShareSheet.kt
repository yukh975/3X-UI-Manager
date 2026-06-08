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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import net.yukh.xui.ui.qr.qrImageBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientShareSheet(
    email: String,
    links: List<String>,
    loading: Boolean,
    error: String?,
    subUrl: String?,
    subChecked: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
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
                title = "Subscription",
                subtitle = "Auto-updating link for all configs",
                initiallyExpanded = true,
            ) {
                when {
                    !subChecked -> LoadingBlock()
                    subUrl != null -> QrAndLink(content = subUrl, context = context)
                    else -> Text(
                        "Subscription URL is unavailable. It requires panel " +
                            "settings access — connect with login/password " +
                            "(an API token can't read panel settings).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // --- Connections --------------------------------------------------
            ExpandableSection(
                title = "Connections" + if (links.isNotEmpty()) " (${links.size})" else "",
                subtitle = "Individual server links",
                initiallyExpanded = false,
            ) {
                when {
                    loading -> LoadingBlock()
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                    links.isEmpty() -> Text(
                        "No connection links for this client.",
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
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text("  Delete")
                }
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Text("  Edit")
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Close") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete client?") },
            text = { Text("$email will be removed from every attached inbound.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
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
                    contentDescription = if (expanded) "Collapse" else "Expand",
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
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
            }
            TextButton(onClick = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share"))
            }) {
                Icon(Icons.Outlined.Share, contentDescription = "Share")
            }
        }
    }
}

@Composable
private fun QrCard(content: String) {
    val bitmap = remember(content) { qrImageBitmap(content, 768) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "QR code",
            modifier = Modifier.size(220.dp),
        )
    }
}

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}
