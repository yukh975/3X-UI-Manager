package net.yukh.xui.app

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * "More" tab: Panels (multi-instance switch / add / sign out) + Settings
 * (language) + App lock + Xray config + About. Mirrors the Android menu.
 */
@Composable
fun MoreScreen(
    host: String,
    lang: String,
    onLang: (String) -> Unit,
    lock: AppLock,
    profiles: List<SavedSession>,
    activeId: String?,
    onSwitch: (SavedSession) -> Unit,
    onAddPanel: () -> Unit,
    onDeleteProfile: (SavedSession) -> Unit,
    onXrayConfig: () -> Unit,
    onGeneralX: () -> Unit,
    onDnsX: () -> Unit,
    onRoutingX: () -> Unit,
    onOutboundsX: () -> Unit,
    onPanelAdmin: () -> Unit,
    onNodeMtls: () -> Unit,
    onBackup: () -> Unit,
) {
    var hasCode by remember { mutableStateOf(lock.hasPasscode()) }
    var bioOn by remember { mutableStateOf(lock.biometryEnabled()) }
    var showSetDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<SavedSession?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(tr("More"), style = MaterialTheme.typography.headlineSmall)

        // ---- Panels (multi-instance): switch / add / remove ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Panels"), style = MaterialTheme.typography.titleMedium)
                profiles.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = p.id == activeId, onClick = { onSwitch(p) })
                        Column(modifier = Modifier.weight(1f).clickable { onSwitch(p) }) {
                            Text(p.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                p.baseUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        // Always available — signing out of the last panel returns
                        // to the Connect screen (this replaces the old Disconnect).
                        TextButton(onClick = { confirmDelete = p }) {
                            Text(tr("Sign out"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedButton(onClick = onAddPanel, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Add panel"))
                }
            }
        }

        // ---- Settings: language ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("Settings"), style = MaterialTheme.typography.titleMedium)
                Text(tr("Language"), style = MaterialTheme.typography.labelMedium)
                LanguageRow(tr("English"), selected = lang == LANG_EN) { onLang(LANG_EN) }
                LanguageRow(tr("Русский"), selected = lang == LANG_RU) { onLang(LANG_RU) }
            }
        }

        // ---- App lock ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("App lock"), style = MaterialTheme.typography.titleMedium)
                if (!hasCode) {
                    OutlinedButton(onClick = { showSetDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Set passcode"))
                    }
                } else {
                    OutlinedButton(onClick = { showSetDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Change passcode"))
                    }
                    if (lock.biometryAvailable()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tr("Unlock with biometrics"), modifier = Modifier.weight(1f))
                            Switch(checked = bioOn, onCheckedChange = { bioOn = it; lock.setBiometryEnabled(it) })
                        }
                    }
                    OutlinedButton(onClick = { lock.removePasscode(); hasCode = false; bioOn = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Remove passcode"))
                    }
                }
            }
        }

        // ---- Xray config ----
        Card(modifier = Modifier.fillMaxWidth().clickable { onXrayConfig() }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Xray config"), style = MaterialTheme.typography.titleMedium)
                Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---- Xray: structured sections ----
        NavCard(tr("Outbounds"), onOutboundsX)
        NavCard(tr("General / Logs"), onGeneralX)
        NavCard(tr("DNS"), onDnsX)
        NavCard(tr("Routing"), onRoutingX)

        // ---- Panel admin ----
        NavCard(tr("Panel admin"), onPanelAdmin)

        // ---- Node mTLS ----
        NavCard(tr("Node mTLS"), onNodeMtls)

        // ---- Backup / restore ----
        NavCard(tr("Backup / restore"), onBackup)

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

    }

    if (showSetDialog) {
        var newCode by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        val valid = newCode.length in 4..8 && newCode == confirm
        AlertDialog(
            onDismissRequest = { showSetDialog = false },
            title = { Text(if (hasCode) tr("Change passcode") else tr("Set passcode")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Set a 4–8 digit passcode"), style = MaterialTheme.typography.labelMedium)
                    PinField(newCode, { newCode = it.filter(Char::isDigit).take(8) }, tr("Passcode"))
                    PinField(confirm, { confirm = it.filter(Char::isDigit).take(8) }, tr("Confirm passcode"))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { lock.setPasscode(newCode); hasCode = true; showSetDialog = false },
                    enabled = valid,
                ) { Text(tr("Save")) }
            },
            dismissButton = { TextButton(onClick = { showSetDialog = false }) { Text(tr("Cancel")) } },
        )
    }

    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(tr("Sign out of this panel?")) },
            text = { Text("${tr("The app will forget its saved URL and token.")}\n\n${p.label}\n${p.baseUrl}") },
            confirmButton = {
                TextButton(onClick = { onDeleteProfile(p); confirmDelete = null }) {
                    Text(tr("Sign out"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(tr("Cancel")) } },
        )
    }
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

/** A tappable card row with a trailing chevron, like the Xray-config entry. */
@Composable
private fun NavCard(title: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
