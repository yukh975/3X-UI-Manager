package net.yukh.xui.ui.screen.nodes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.yukh.xui.i18n.tr

/**
 * Mutual-TLS between panels (panel v3.4.0): copy this panel's CA to register on a
 * node, and set the CA whose client certs this panel trusts when it acts as a node.
 */
@Composable
fun NodeMtlsDialog(
    panelCa: String?,
    busy: Boolean,
    onSaveTrustCa: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var trustCa by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Node mTLS")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    tr("This panel's CA — register it on a node that uses Mutual TLS."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (panelCa == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).padding(top = 8.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("CA", panelCa))
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  " + tr("Copy this panel's CA"))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    tr("Trusted parent CA — paste a managing panel's CA so it can manage this panel as a node (applied on restart)."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = trustCa,
                    onValueChange = { trustCa = it },
                    label = { Text(tr("Trusted parent CA (PEM)")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSaveTrustCa(trustCa) }, enabled = !busy) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
    )
}
