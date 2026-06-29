package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.yukh.xui.shared.api.PanelApi

/**
 * Panel-wide mTLS (node certificate trust): export this panel's CA so a parent
 * panel can trust it, or set the parent CA this panel trusts when it runs as a
 * node. A standalone page reached from the More tab, mirroring the Android
 * "Node mTLS" menu entry (it used to be a card on the Nodes screen). Panel v3.4+.
 */
@Composable
fun MtlsScreen(api: PanelApi, lang: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text(tr("Back")) }
            Text(tr("Node mTLS"), style = MaterialTheme.typography.titleMedium)
            Box(Modifier.padding(end = 8.dp))
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                tr("Share this panel's CA so a parent panel can trust it, or set the parent CA this panel trusts when it runs as a node."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val r = try { api.nodeMtlsCa() } catch (e: Throwable) { null }
                                val ca = r?.obj?.caCert ?: ""
                                if (ca.isNotBlank()) {
                                    platformExportFile("panel-ca.pem", ca.encodeToByteArray())
                                    message = tr(lang, "Exported this panel's CA.")
                                } else {
                                    message = tr(lang, "Couldn't read the panel CA.")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("Export this panel's CA")) }
                    OutlinedButton(
                        onClick = {
                            platformPickFile { _, bytes ->
                                scope.launch {
                                    message = try {
                                        api.setNodeMtlsTrustCA(bytes.decodeToString())
                                        tr(lang, "Trusted parent CA set.")
                                    } catch (e: Throwable) {
                                        e.message ?: tr(lang, "Couldn't set the trusted CA.")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("Set trusted parent CA")) }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
