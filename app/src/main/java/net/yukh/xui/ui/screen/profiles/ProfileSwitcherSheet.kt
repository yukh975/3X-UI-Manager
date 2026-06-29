package net.yukh.xui.ui.screen.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.yukh.xui.data.prefs.ConnectionProfile
import net.yukh.xui.i18n.tr

/**
 * Bottom sheet listing the saved panel profiles. Tap one to make it active
 * (switches the connection), add another, or remove one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitcherSheet(
    profiles: List<ConnectionProfile>,
    activeId: String?,
    onSwitch: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var confirmDelete by remember { mutableStateOf<ConnectionProfile?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                tr("Panels"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            profiles.forEach { p ->
                val active = p.id == activeId
                ListItem(
                    modifier = Modifier.clickable {
                        if (!active) onSwitch(p.id)
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (active) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface,
                    ),
                    leadingContent = {
                        Icon(
                            if (active) Icons.Filled.CheckCircle else Icons.Outlined.Dns,
                            contentDescription = null,
                            tint = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    headlineContent = { Text(p.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(p.baseUrl, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium)
                    },
                    trailingContent = {
                        IconButton(onClick = { confirmDelete = p }) {
                            Icon(Icons.Outlined.Delete, contentDescription = tr("Delete"),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            }

            HorizontalDivider()

            ListItem(
                modifier = Modifier.clickable { onAdd() },
                leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                headlineContent = { Text(tr("Add panel")) },
            )
        }
    }

    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(tr("Remove panel?")) },
            text = { Text(p.label + "\n\n" + tr("This forgets the saved URL and token for this panel.")) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; onDelete(p.id) }) {
                    Text(tr("Remove"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(tr("Cancel")) } },
        )
    }
}
