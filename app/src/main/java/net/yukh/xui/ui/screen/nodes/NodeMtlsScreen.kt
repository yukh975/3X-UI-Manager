package net.yukh.xui.ui.screen.nodes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
 *
 * A full-screen page (same Scaffold + TopAppBar layout and colours as the DNS /
 * Outbounds / Routing editors), rendered as an overlay in MainScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeMtlsScreen(
    panelCa: String?,
    busy: Boolean,
    onSaveTrustCa: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var trustCa by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Node mTLS")) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close"))
                    }
                },
                actions = {
                    TextButton(onClick = { onSaveTrustCa(trustCa) }, enabled = !busy && trustCa.isNotBlank()) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(tr("Save"))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                tr("This panel's CA — register it on a node that uses Mutual TLS."),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (panelCa == null) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("CA", panelCa))
                }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  " + tr("Copy this panel's CA"))
                }
            }

            HorizontalDivider()

            Text(
                tr("Trusted parent CA — paste a managing panel's CA so it can manage this panel as a node (applied on restart)."),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = trustCa,
                onValueChange = { trustCa = it },
                label = { Text(tr("Trusted parent CA (PEM)")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            )
        }
    }
}
