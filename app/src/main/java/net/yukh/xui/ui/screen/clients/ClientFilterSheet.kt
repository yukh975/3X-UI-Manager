package net.yukh.xui.ui.screen.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.yukh.xui.i18n.tr

/**
 * Status + group filter for the client list, mirroring the panel's filter
 * drawer (web `FilterDrawer`) as a bottom sheet. Each section narrows
 * independently; selecting nothing in a section means "no constraint".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClientFilterSheet(
    filters: ClientFilters,
    groups: List<String>,
    sheetState: SheetState,
    onToggleStatus: (ClientStatus) -> Unit,
    onToggleGroup: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tr("Filters"), style = MaterialTheme.typography.titleLarge)

            Text(tr("Status"), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClientStatus.entries.forEach { s ->
                    FilterChip(
                        selected = s in filters.statuses,
                        onClick = { onToggleStatus(s) },
                        label = { Text(statusLabel(s)) },
                    )
                }
            }

            if (groups.isNotEmpty()) {
                Text(tr("Group"), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { g ->
                        FilterChip(
                            selected = g in filters.groups,
                            onClick = { onToggleGroup(g) },
                            label = { Text(g) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onClear,
                    enabled = !filters.isEmpty,
                    modifier = Modifier.weight(1f),
                ) { Text(tr("Clear all")) }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(tr("Done")) }
            }
        }
    }
}

@Composable
private fun statusLabel(s: ClientStatus): String = when (s) {
    ClientStatus.ONLINE -> tr("Online")
    ClientStatus.ACTIVE -> tr("Active")
    ClientStatus.DISABLED -> tr("Disabled")
    ClientStatus.DEPLETED -> tr("Depleted")
}
