package net.yukh.xui.ui.screen.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.yukh.xui.data.api.dto.Client
import net.yukh.xui.i18n.LocalAppLanguage
import net.yukh.xui.i18n.tr
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

    LaunchedEffect(Unit) { vm.load() }

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
                                onClick = { vm.openShareSheet(client.email) },
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

    // The client editor is rendered as a full-screen overlay by MainScreen
    // (activity window) so its insets/keyboard handling work correctly.
}

@Composable
private fun ClientRow(
    client: Client,
    online: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (client.enable) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (online) MaterialTheme.colorScheme.primary
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
