package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import net.yukh.xui.shared.api.PanelApi
import net.yukh.xui.shared.api.PanelFeatureUnsupportedException
import net.yukh.xui.shared.dto.OutboundSubscription

/** One line of a subscription preview: the tag the panel would assign and the
 *  protocol behind it. */
private data class PreviewedOutbound(val tag: String, val protocol: String)

/**
 * Outbound subscriptions — remote lists of outbounds the panel fetches on a
 * timer and merges into the Xray config (the panel's "Subscriptions" panel on
 * the Outbounds page).
 *
 * Every mutation only flags Xray for a restart, so the screen says so and
 * offers to do it.
 */
@Composable
fun OutboundSubsScreen(api: PanelApi, lang: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var unsupported by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<OutboundSubscription>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<OutboundSubscription?>(null) }
    var previewing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<List<PreviewedOutbound>?>(null) }
    var deleteTarget by remember { mutableStateOf<OutboundSubscription?>(null) }

    val restartHint = tr(lang, "Saved — restart Xray to apply")

    suspend fun load() {
        loading = true
        error = null
        try {
            val r = api.listOutboundSubs()
            items = (r.obj ?: emptyList()).sortedBy { it.priority }
            unsupported = false
        } catch (e: PanelFeatureUnsupportedException) {
            unsupported = true
        } catch (e: Throwable) {
            error = e.message
        }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    fun mutate(block: suspend () -> Boolean) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val ok = try { block() } catch (e: Throwable) { error = e.message; false }
            busy = false
            if (ok) {
                message = restartHint
                load()
            }
        }
    }

    if (editor != null) {
        val draft = editor!!
        SubscriptionEditorDialog(
            draft = draft,
            busy = busy,
            previewing = previewing,
            preview = preview,
            onChange = { transform -> editor = transform(draft) },
            onPreview = {
                if (draft.url.isNotBlank() && !previewing) {
                    previewing = true
                    preview = null
                    error = null
                    scope.launch {
                        try {
                            val r = api.parseOutboundSub(draft.url, draft.allowPrivate, draft.allowInsecure)
                            preview = (r.obj ?: emptyList()).map(::toPreview)
                        } catch (e: Throwable) {
                            error = e.message
                        }
                        previewing = false
                    }
                }
            },
            onSave = {
                if (draft.url.isNotBlank() && !busy) {
                    busy = true
                    error = null
                    scope.launch {
                        val ok = try {
                            val r = if (draft.id == 0) api.createOutboundSub(draft) else api.updateOutboundSub(draft)
                            r.success
                        } catch (e: Throwable) {
                            error = e.message
                            false
                        }
                        busy = false
                        if (ok) {
                            editor = null
                            preview = null
                            message = restartHint
                            load()
                        }
                    }
                }
            },
            onDismiss = { editor = null; preview = null; previewing = false },
        )
    }

    deleteTarget?.let { sub ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(tr("Delete this subscription?")) },
            text = { Text(sub.remark.ifBlank { sub.url }) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    mutate { api.deleteOutboundSub(sub.id).success }
                }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(tr("Cancel")) } },
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text(tr("Back")) }
                Text(tr("Outbound subscriptions"), style = MaterialTheme.typography.titleMedium)
                if (!unsupported && items.any { it.enabled }) {
                    TextButton(
                        onClick = {
                            mutate {
                                var failed = 0
                                items.filter { it.enabled }.forEach { sub ->
                                    val ok = try { api.refreshOutboundSub(sub.id).success } catch (e: Throwable) { false }
                                    if (!ok) failed++
                                }
                                if (failed > 0) error = tr(lang, "Some subscriptions failed to refresh")
                                true
                            }
                        },
                        enabled = !busy,
                    ) { Text(tr("Refresh all")) }
                } else {
                    Box(Modifier.padding(end = 8.dp))
                }
            }

            message?.let {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = {
                            message = null
                            scope.launch {
                                busy = true
                                try { api.restartXray() } catch (e: Throwable) { error = e.message }
                                busy = false
                            }
                        },
                        enabled = !busy,
                    ) { Text(tr("Restart")) }
                }
            }
            error?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                unsupported -> PanelFeatureUnsupported(tr("Outbound subscriptions"))

                else -> Column(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (items.isEmpty()) {
                        Text(tr("No subscriptions yet."), style = MaterialTheme.typography.titleMedium)
                        Text(
                            tr("Add a subscription URL and the panel will keep its servers available as outbounds."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items.forEach { sub ->
                        SubscriptionCard(
                            sub = sub,
                            busy = busy,
                            onToggle = { v -> mutate { api.updateOutboundSub(sub.copy(enabled = v)).success } },
                            onRefresh = { mutate { api.refreshOutboundSub(sub.id).success } },
                            onEdit = { editor = sub; preview = null },
                            onDelete = { deleteTarget = sub },
                            onMove = { up -> mutate { api.moveOutboundSub(sub.id, up).success } },
                        )
                    }
                    OutlinedButton(
                        onClick = { editor = OutboundSubscription(); preview = null },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("Add subscription")) }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    sub: OutboundSubscription,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        sub.remark.ifBlank { tr("Subscription") },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        sub.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = sub.enabled, onCheckedChange = onToggle, enabled = !busy)
            }

            val lastFetch = if (sub.lastUpdated <= 0) tr("never") else formatDayMonth(sub.lastUpdated)
            Text(
                "${tr("Outbounds")}: ${sub.outboundCount}   ·   ${tr("Last fetch")}: $lastFetch   ·   " +
                    "${tr("Every")} ${sub.updateInterval / 60} ${tr("min")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sub.tagPrefix.isNotBlank() || sub.prepend) {
                Text(
                    listOfNotNull(
                        sub.tagPrefix.takeIf { it.isNotBlank() }?.let { "${tr("Tag prefix")}: $it" },
                        if (sub.prepend) tr("Before manual outbounds") else null,
                    ).joinToString("   ·   "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sub.lastError.isNotBlank()) {
                Text(
                    sub.lastError,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRefresh, enabled = !busy) { Text(tr("Refresh")) }
                TextButton(onClick = onEdit, enabled = !busy) { Text(tr("Edit")) }
                TextButton(onClick = { onMove(true) }, enabled = !busy) { Text("↑") }
                TextButton(onClick = { onMove(false) }, enabled = !busy) { Text("↓") }
                Box(Modifier.weight(1f))
                TextButton(onClick = onDelete, enabled = !busy) {
                    Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SubscriptionEditorDialog(
    draft: OutboundSubscription,
    busy: Boolean,
    previewing: Boolean,
    preview: List<PreviewedOutbound>?,
    onChange: ((OutboundSubscription) -> OutboundSubscription) -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == 0) tr("Add subscription") else tr("Edit subscription")) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.url,
                    onValueChange = { v -> onChange { it.copy(url = v) } },
                    label = { Text(tr("Subscription URL")) },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.remark,
                    onValueChange = { v -> onChange { it.copy(remark = v) } },
                    label = { Text(tr("Remark (optional)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.tagPrefix,
                    onValueChange = { v -> onChange { it.copy(tagPrefix = v) } },
                    label = { Text(tr("Tag prefix")) },
                    placeholder = { Text("hk-") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = (draft.updateInterval / 60).toString(),
                    onValueChange = { v ->
                        val minutes = v.filter { c -> c.isDigit() }.take(5).toIntOrNull() ?: 0
                        onChange { it.copy(updateInterval = (minutes * 60).coerceAtLeast(60)) }
                    },
                    label = { Text(tr("Update interval, min")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(tr("Enabled"), draft.enabled) { v -> onChange { it.copy(enabled = v) } }
                SwitchRow(tr("Before manual outbounds"), draft.prepend) { v -> onChange { it.copy(prepend = v) } }
                SwitchRow(tr("Allow private address"), draft.allowPrivate) { v -> onChange { it.copy(allowPrivate = v) } }
                SwitchRow(tr("Allow insecure TLS"), draft.allowInsecure) { v -> onChange { it.copy(allowInsecure = v) } }

                OutlinedButton(
                    onClick = onPreview,
                    enabled = draft.url.isNotBlank() && !previewing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (previewing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(tr("Preview"))
                }
                preview?.let { list ->
                    if (list.isEmpty()) {
                        Text(
                            tr("No outbounds found at this URL."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            "${tr("Found")}: ${list.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        list.take(12).forEach { o ->
                            Text(
                                "• ${o.tag}${if (o.protocol.isNotBlank()) "  (${o.protocol})" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (list.size > 12) Text("…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = draft.url.isNotBlank() && !busy) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun toPreview(o: JsonObject) = PreviewedOutbound(
    tag = o["tag"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    protocol = o["protocol"]?.jsonPrimitive?.contentOrNull.orEmpty(),
)
