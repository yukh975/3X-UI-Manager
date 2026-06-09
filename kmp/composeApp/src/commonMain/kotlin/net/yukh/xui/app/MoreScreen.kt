package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * "More" tab: Settings (language) + About (version/copyright) + Disconnect.
 * First step toward feature parity with the native Android app's menu.
 */
@Composable
fun MoreScreen(
    host: String,
    lang: String,
    onLang: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(tr("More"), style = MaterialTheme.typography.headlineSmall)

        // ---- Settings: language ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("Settings"), style = MaterialTheme.typography.titleMedium)
                Text(tr("Language"), style = MaterialTheme.typography.labelMedium)
                LanguageRow(tr("English"), selected = lang == LANG_EN) { onLang(LANG_EN) }
                LanguageRow(tr("Русский"), selected = lang == LANG_RU) { onLang(LANG_RU) }
            }
        }

        // ---- About ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("About"), style = MaterialTheme.typography.titleMedium)
                Text("${tr("Application")}: 3X-UI Manager", style = MaterialTheme.typography.bodyMedium)
                Text("${tr("Version")}: ${appVersionName()}", style = MaterialTheme.typography.bodyMedium)
                if (host.isNotBlank()) {
                    Text(
                        "${tr("Panel URL:")} $host",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "© 2026 Yuriy Khachaturian (yukh.net)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) { Text(tr("Disconnect")) }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
