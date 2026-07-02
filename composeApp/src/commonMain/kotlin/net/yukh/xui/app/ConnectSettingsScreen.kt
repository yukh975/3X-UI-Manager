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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Lightweight settings reachable from the Connect screen (before sign-in), so
 * language / version / the update check are available on a fresh install — the
 * full "More" tab only exists once connected. No app-lock here (nothing to lock
 * yet; the lock guards the signed-in UI).
 */
@Composable
fun ConnectSettingsScreen(
    lang: String,
    onLang: (String) -> Unit,
    onCheckUpdates: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Text(tr("Back"))
            }
            Text(
                tr("Settings"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // ---- Language ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("Language"), style = MaterialTheme.typography.titleMedium)
                LangRow(tr("English"), lang == LANG_EN) { onLang(LANG_EN) }
                LangRow(tr("Русский"), lang == LANG_RU) { onLang(LANG_RU) }
            }
        }

        // ---- Panel alerts ----
        PanelAlertsCard()

        // ---- About ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("About"), style = MaterialTheme.typography.titleMedium)
                Text("${tr("Application")}: 3X-UI Manager", style = MaterialTheme.typography.bodyMedium)
                Text("${tr("Version")}: ${appVersionName()}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "© 2026 Yuriy Khachaturian (yukh.net)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onCheckUpdates, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Check for updates"))
                }
            }
        }
    }
}

@Composable
private fun LangRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
