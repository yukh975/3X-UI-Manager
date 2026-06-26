package net.yukh.xui.ui.screen.clients

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.Json
import net.yukh.xui.data.api.dto.Client
import net.yukh.xui.i18n.LocalAppLanguage
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.ExportJsonDialog
import net.yukh.xui.ui.components.ImportJsonDialog
import net.yukh.xui.ui.components.LabeledDropdown
import net.yukh.xui.ui.format.formatBytes
import net.yukh.xui.ui.format.formatExpiryDays
import net.yukh.xui.ui.format.formatLastOnline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    vm: ClientsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showFilters by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var clientsMenu by remember { mutableStateOf(false) }
    var showImportClients by remember { mutableStateOf(false) }
    var confirmDeleteOrphans by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    // Auto-refresh the list while the screen is on-screen (start/stop with lifecycle).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.startPolling()
                Lifecycle.Event.ON_STOP -> vm.stopPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); vm.stopPolling() }
    }

    // Hardware back exits selection mode first.
    BackHandler(enabled = state.selectionMode) { vm.exitSelection() }

    LaunchedEffect(state.transientMessage) {
        state.transientMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.dismissMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null && state.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            state.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text(tr("No clients yet.")) }

            else -> Column(modifier = Modifier.fillMaxSize()) {
                if (state.selectionMode) {
                    SelectionBar(
                        count = state.selectedEmails.size,
                        busy = state.bulkInFlight,
                        onClose = vm::exitSelection,
                        onSelectAll = vm::selectAllVisible,
                        onEnable = { vm.bulkSetEnabled(true) },
                        onDisable = { vm.bulkSetEnabled(false) },
                        onAdjust = { showAdjust = true },
                        onDelete = { confirmBulkDelete = true },
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = vm::setSearchQuery,
                            label = { Text(tr("Search by email")) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { vm.setSearchQuery("") }) {
                                        Icon(Icons.Filled.Clear, contentDescription = tr("Clear"))
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { showFilters = true }) {
                            BadgedBox(
                                badge = {
                                    if (state.filters.count > 0) Badge { Text(state.filters.count.toString()) }
                                },
                            ) {
                                Icon(Icons.Filled.FilterList, contentDescription = tr("Filters"))
                            }
                        }
                        Box {
                            IconButton(onClick = { clientsMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = tr("More"))
                            }
                            DropdownMenu(expanded = clientsMenu, onDismissRequest = { clientsMenu = false }) {
                                DropdownMenuItem(text = { Text(tr("Export clients")) }, onClick = { clientsMenu = false; vm.exportClients() })
                                DropdownMenuItem(text = { Text(tr("Import clients")) }, onClick = { clientsMenu = false; showImportClients = true })
                                DropdownMenuItem(text = { Text(tr("Delete unbound clients")) }, onClick = { clientsMenu = false; confirmDeleteOrphans = true })
                            }
                        }
                    }
                }

                val visible = state.visibleItems
                if (visible.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.searchQuery.isNotBlank())
                                "${tr("No clients match")} \"${state.searchQuery}\"."
                            else tr("No clients match the filters"),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(visible, key = { it.email.ifBlank { it.id.toString() } }) { client ->
                            ClientRow(
                                client = client,
                                online = client.email in state.online,
                                selected = client.email in state.selectedEmails,
                                selectionMode = state.selectionMode,
                                onClick = {
                                    if (state.selectionMode) vm.toggleSelected(client.email)
                                    else vm.openShareSheet(client.email)
                                },
                                onLongClick = { vm.startSelection(client.email) },
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = vm::openCreateEditor,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = tr("Add client"))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { Snackbar { Text(it.visuals.message) } }
    }

    val selectedEmail = state.selectedClientEmail
    if (selectedEmail != null) {
        ClientShareSheet(
            email = selectedEmail,
            links = state.selectedLinks,
            loading = state.linksLoading,
            error = state.linksError,
            subUrl = state.selectedSubUrl,
            subChecked = state.subUrlChecked,
            sheetState = sheetState,
            onDismiss = vm::closeShareSheet,
            onEdit = { vm.openEditEditor(selectedEmail) },
            onDelete = { vm.deleteClient(selectedEmail) },
            onIpLog = { vm.openIpLog(selectedEmail); vm.closeShareSheet() },
        )
    }

    state.ipLogEmail?.let { email ->
        AlertDialog(
            onDismissRequest = vm::closeIpLog,
            title = { Text("${tr("IP log")} · $email") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when {
                        state.ipLogLoading -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        state.ipLog.isEmpty() -> Text(tr("No IPs logged."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> state.ipLog.forEach { e ->
                            Column {
                                Text(e.ip, style = MaterialTheme.typography.bodyMedium)
                                val sub = listOfNotNull(
                                    e.time.takeIf { it.isNotBlank() },
                                    e.node.takeIf { it.isNotBlank() }?.let { "@ $it" },
                                ).joinToString("  ")
                                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::clearIpLog, enabled = state.ipLog.isNotEmpty()) { Text(tr("Clear")) }
            },
            dismissButton = { TextButton(onClick = vm::closeIpLog) { Text(tr("Close")) } },
        )
    }

    if (showFilters) {
        ClientFilterSheet(
            filters = state.filters,
            groups = state.allGroups,
            sheetState = filterSheetState,
            onToggleStatus = vm::toggleStatusFilter,
            onToggleGroup = vm::toggleGroupFilter,
            onClear = vm::clearFilters,
            onDismiss = { showFilters = false },
        )
    }

    if (showAdjust) {
        BulkAdjustDialog(
            count = state.selectedEmails.size,
            onApply = { addDays, addBytes, flow ->
                vm.bulkAdjust(addDays, addBytes, flow)
                showAdjust = false
            },
            onDismiss = { showAdjust = false },
        )
    }

    if (confirmBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text(tr("Delete selected clients?")) },
            text = { Text("${state.selectedEmails.size} ${tr("selected")}") },
            confirmButton = {
                TextButton(onClick = { vm.bulkDelete(); confirmBulkDelete = false }) {
                    Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) { Text(tr("Cancel")) } },
        )
    }

    state.exportJson?.let { json ->
        ExportJsonDialog(title = tr("Export clients"), json = json, onDismiss = vm::dismissExport)
    }

    if (showImportClients) {
        val invalidMsg = tr("Invalid JSON")
        ImportJsonDialog(
            title = tr("Import clients"),
            onImport = { txt ->
                if (runCatching { Json.parseToJsonElement(txt.trim()) }.isFailure) invalidMsg
                else { vm.importClients(txt.trim()); null }
            },
            onDismiss = { showImportClients = false },
        )
    }

    if (confirmDeleteOrphans) {
        AlertDialog(
            onDismissRequest = { confirmDeleteOrphans = false },
            title = { Text(tr("Delete unbound clients?")) },
            text = { Text(tr("Removes every client not attached to any inbound, with their traffic and links. This can't be undone.")) },
            confirmButton = {
                TextButton(onClick = { vm.deleteOrphanClients(); confirmDeleteOrphans = false }) {
                    Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteOrphans = false }) { Text(tr("Cancel")) } },
        )
    }

    // The client editor is rendered as a full-screen overlay by MainScreen
    // (activity window) so its insets/keyboard handling work correctly.
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClientRow(
    client: Client,
    online: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.primaryContainer
                client.enable -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            // Online = clear green; offline = muted gray.
                            color = if (online) Color(0xFF34C759)
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = client.email.ifBlank { tr("(no email)") },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!client.enable) {
                    Text(
                        tr("disabled"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("↑ ${client.up.formatBytes()}", style = MaterialTheme.typography.labelMedium)
                Text("↓ ${client.down.formatBytes()}", style = MaterialTheme.typography.labelMedium)
                if (client.quota > 0) {
                    Text(
                        "${tr("of")} ${client.quota.formatBytes()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "${tr("Expires")}: ${client.expiryTime.formatExpiryDays(LocalAppLanguage.current)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val lastSeen = if (client.lastOnline <= 0L) tr("Never")
                else client.lastOnline.formatLastOnline(LocalAppLanguage.current)
            Text(
                "${tr("Last seen")}: $lastSeen",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    busy: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onAdjust: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = tr("Cancel")) }
        Text(
            "$count ${tr("selected")}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        IconButton(onClick = onSelectAll) { Icon(Icons.Filled.DoneAll, contentDescription = tr("Select all")) }
        Box {
            IconButton(onClick = { menu = true }, enabled = !busy) {
                Icon(Icons.Filled.MoreVert, contentDescription = tr("More"))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(tr("Enable")) }, onClick = { menu = false; onEnable() })
                DropdownMenuItem(text = { Text(tr("Disable")) }, onClick = { menu = false; onDisable() })
                DropdownMenuItem(text = { Text(tr("Adjust") + "…") }, onClick = { menu = false; onAdjust() })
                DropdownMenuItem(text = { Text(tr("Delete")) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun BulkAdjustDialog(
    count: Int,
    onApply: (Int, Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var days by remember { mutableStateOf("") }
    var gb by remember { mutableStateOf("") }
    var flow by remember { mutableStateOf("") }
    // API flow value → display label (matches the panel's bulk Adjust flow set).
    val flowLabels = linkedMapOf(
        "" to tr("No change"),
        "none" to tr("Clear flow"),
        "xtls-rprx-vision" to "xtls-rprx-vision",
        "xtls-rprx-vision-udp443" to "xtls-rprx-vision-udp443",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${tr("Adjust")} ($count)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text(tr("Add days (+/-)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = gb,
                    onValueChange = { gb = it.filter { c -> c.isDigit() || c == '-' || c == '.' } },
                    label = { Text(tr("Add traffic (GB, +/-)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                LabeledDropdown(tr("Set flow"), flowLabels[flow] ?: "", flowLabels.values.toList()) { sel ->
                    flow = flowLabels.entries.first { it.value == sel }.key
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val addDays = days.toIntOrNull() ?: 0
                val addBytes = ((gb.toDoubleOrNull() ?: 0.0) * 1024.0 * 1024.0 * 1024.0).toLong()
                onApply(addDays, addBytes, flow)
            }) { Text(tr("Apply")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
