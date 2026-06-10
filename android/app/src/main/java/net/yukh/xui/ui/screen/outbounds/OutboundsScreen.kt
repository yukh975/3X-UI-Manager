package net.yukh.xui.ui.screen.outbounds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.yukh.xui.data.api.dto.outboundAddressSummary
import net.yukh.xui.data.api.dto.outboundProtocol
import net.yukh.xui.data.api.dto.outboundTag
import net.yukh.xui.data.parse.parseVlessLink
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboundsScreen(
    onClose: () -> Unit,
    vm: OutboundsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let { snackbar.showSnackbar(it); vm.dismissSavedMessage() }
    }
    LaunchedEffect(state.error) {
        if (state.available) state.error?.let { snackbar.showSnackbar(it); vm.dismissError() }
    }

    // --- Editor overlay ---
    val editing = state.editing
    if (editing != null) {
        var confirmDelete by remember { mutableStateOf(false) }
        OutboundEditorScreen(
            editing = editing,
            error = state.editorError,
            onDraftChange = vm::updateDraft,
            onDone = vm::applyDraft,
            onCancel = vm::closeEditor,
            onDelete = if (!editing.isNew) ({ confirmDelete = true }) else null,
        )
        if (confirmDelete) {
            ConfirmDialog(
                title = tr("Delete outbound?"),
                text = editing.draft.outboundTag().ifBlank { tr("This outbound") },
                confirmLabel = tr("Delete"),
                destructive = true,
                onConfirm = {
                    confirmDelete = false
                    val i = editing.index
                    vm.closeEditor()
                    vm.deleteAt(i)
                },
                onDismiss = { confirmDelete = false },
            )
        }
        return
    }

    // --- List ---
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }
    val invalidLink = tr("Not a valid vless:// link")
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(tr("Outbounds")) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close"))
                    }
                },
                actions = {
                    if (state.available) {
                        IconButton(onClick = { showImport = true }) {
                            Icon(Icons.Outlined.Link, contentDescription = tr("Import from link"))
                        }
                        TextButton(onClick = vm::save, enabled = state.dirty && !state.saving) {
                            if (state.saving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(tr("Save"))
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.available) {
                FloatingActionButton(onClick = vm::openNew) {
                    Icon(Icons.Filled.Add, contentDescription = tr("Add outbound"))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar { Text(it.visuals.message) } } },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                !state.available -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        tr("Xray configuration isn't available with an API token."),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        tr("The panel only exposes the Xray config (outbounds, routing, ") +
                            tr("DNS) to a logged-in session. Reconnect with login/password ") +
                            tr("to edit it."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                ) {
                    itemsIndexed(state.outbounds) { index, ob ->
                        OutboundRow(
                            index = index,
                            total = state.outbounds.size,
                            tag = ob.outboundTag(),
                            protocol = ob.outboundProtocol(),
                            address = ob.outboundAddressSummary(),
                            onClick = { vm.openEdit(index) },
                            onUp = { vm.move(index, -1) },
                            onDown = { vm.move(index, +1) },
                            onDelete = { pendingDelete = index },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { idx ->
        val tag = state.outbounds.getOrNull(idx)?.outboundTag().orEmpty()
        ConfirmDialog(
            title = tr("Delete outbound?"),
            text = tag.ifBlank { tr("This outbound") },
            confirmLabel = tr("Delete"),
            destructive = true,
            onConfirm = { pendingDelete = null; vm.deleteAt(idx) },
            onDismiss = { pendingDelete = null },
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false; linkText = ""; linkError = null },
            title = { Text(tr("Import from vless:// link")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = linkText,
                        onValueChange = { linkText = it; linkError = null },
                        label = { Text(tr("Paste vless:// link")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    linkError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = parseVlessLink(linkText)
                    if (parsed == null) {
                        linkError = invalidLink
                    } else {
                        showImport = false; linkText = ""; linkError = null
                        vm.openImported(parsed)
                    }
                }) { Text(tr("Import")) }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false; linkText = ""; linkError = null }) { Text(tr("Cancel")) }
            },
        )
    }
}

@Composable
private fun OutboundRow(
    index: Int,
    total: Int,
    tag: String,
    protocol: String,
    address: String,
    onClick: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("#${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(tag.ifBlank { "(no tag)" }, style = MaterialTheme.typography.titleSmall)
                val sub = buildString {
                    append(protocol)
                    if (address.isNotBlank()) append("  ·  ").also { append(address) }
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (index == 0) {
                    Text(tr("default route"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onUp, enabled = index > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = tr("Move up"))
            }
            IconButton(onClick = onDown, enabled = index < total - 1) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = tr("Move down"))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = tr("Delete"))
            }
        }
    }
}
