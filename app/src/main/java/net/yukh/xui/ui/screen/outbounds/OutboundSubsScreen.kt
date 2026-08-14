package net.yukh.xui.ui.screen.outbounds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.yukh.xui.data.api.dto.OutboundSubscription
import net.yukh.xui.i18n.LocalAppLanguage
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.PanelFeatureUnsupported
import net.yukh.xui.ui.format.formatLastOnline

/**
 * Outbound subscriptions — remote lists of outbounds the panel fetches on a
 * timer and merges into the Xray config (the panel's "Subscriptions" panel on
 * the Outbounds page).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboundSubsScreen(onClose: () -> Unit, vm: OutboundSubsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<OutboundSubscription?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            val restart = snackbar.showSnackbar(it, actionLabel = "Xray")
            if (restart == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.restartXray()
            vm.dismissMessage()
        }
    }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(tr("Outbound subscriptions")) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close"))
                    }
                },
                actions = {
                    if (!state.unsupported && state.items.any { it.enabled }) {
                        IconButton(onClick = vm::refreshAll, enabled = !state.busy) {
                            Icon(Icons.Outlined.Refresh, contentDescription = tr("Refresh all"))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.unsupported && !state.loading) {
                FloatingActionButton(onClick = vm::openNew) {
                    Icon(Icons.Filled.Add, contentDescription = tr("Add subscription"))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(action = it.visuals.actionLabel?.let { _ -> {
            TextButton(onClick = { it.performAction() }) { Text(tr("Restart")) }
        } }) { Text(it.visuals.message) } } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                state.unsupported -> PanelFeatureUnsupported(tr("Outbound subscriptions"))

                state.items.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(tr("No subscriptions yet."), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("Add a subscription URL and the panel will keep its servers available as outbounds."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.id }) { sub ->
                        SubscriptionCard(
                            sub = sub,
                            busy = state.busy,
                            onToggle = { vm.setEnabled(sub, it) },
                            onRefresh = { vm.refresh(sub.id) },
                            onEdit = { vm.openEdit(sub) },
                            onDelete = { deleteTarget = sub },
                            onMove = { up -> vm.move(sub.id, up) },
                        )
                    }
                }
            }
        }
    }

    state.editor?.let { draft ->
        SubscriptionEditorDialog(
            draft = draft,
            busy = state.busy,
            previewing = state.previewing,
            preview = state.preview,
            onChange = vm::editDraft,
            onPreview = vm::preview,
            onSave = vm::save,
            onDismiss = vm::closeEditor,
        )
    }

    deleteTarget?.let { sub ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(tr("Delete this subscription?")) },
            text = { Text(sub.remark.ifBlank { sub.url }) },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; vm.delete(sub.id) }) {
                    Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(tr("Cancel")) } },
        )
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

            val lastFetch = if (sub.lastUpdated <= 0) tr("never")
            else sub.lastUpdated.formatLastOnline(LocalAppLanguage.current)
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
                IconButton(onClick = onRefresh, enabled = !busy) {
                    Icon(Icons.Outlined.Refresh, contentDescription = tr("Refresh now"))
                }
                IconButton(onClick = onEdit, enabled = !busy) {
                    Icon(Icons.Outlined.Edit, contentDescription = tr("Edit"))
                }
                IconButton(onClick = { onMove(true) }, enabled = !busy) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = tr("Move up"))
                }
                IconButton(onClick = { onMove(false) }, enabled = !busy) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = tr("Move down"))
                }
                Box(Modifier.weight(1f))
                IconButton(onClick = onDelete, enabled = !busy) {
                    Icon(Icons.Outlined.Delete, contentDescription = tr("Delete"), tint = MaterialTheme.colorScheme.error)
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
                        if (list.size > 12) {
                            Text("…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = draft.url.isNotBlank() && !busy) { Text(tr("Save")) }
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
