package net.yukh.xui.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.yukh.xui.i18n.LANG_EN
import net.yukh.xui.i18n.LANG_RU
import net.yukh.xui.i18n.tr
import net.yukh.xui.security.BiometricAuth
import androidx.compose.material3.OutlinedButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onCheckUpdates: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val lang by vm.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }

    var hasPasscode by remember { mutableStateOf(vm.hasPasscode()) }
    var biometric by remember { mutableStateOf(vm.biometricEnabled()) }
    var showSetPasscode by remember { mutableStateOf(false) }
    val biometricAvailable = remember { BiometricAuth.canAuthenticate(context) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(tr("Settings")) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close"))
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Language ----
            Text(tr("Language"), style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    LanguageRow("English", selected = lang == LANG_EN) { vm.setLanguage(LANG_EN) }
                    HorizontalDivider()
                    LanguageRow("Русский", selected = lang == LANG_RU) { vm.setLanguage(LANG_RU) }
                }
            }

            // ---- App lock ----
            Text(tr("App lock"), style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    SettingRow(
                        title = if (hasPasscode) tr("Change passcode") else tr("Set passcode"),
                        onClick = { showSetPasscode = true },
                    )
                    if (hasPasscode) {
                        HorizontalDivider()
                        SettingRow(
                            title = tr("Remove passcode"),
                            titleColor = MaterialTheme.colorScheme.error,
                            onClick = {
                                vm.removePasscode()
                                hasPasscode = false
                                biometric = false
                            },
                        )
                        if (biometricAvailable) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    tr("Unlock with fingerprint"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = biometric,
                                    onCheckedChange = { vm.setBiometricEnabled(it); biometric = it },
                                )
                            }
                        }
                    }
                }
            }

            // ---- About ----
            Text(tr("About"), style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("3X-UI Manager", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${tr("Version")}: $appVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("© 2026 Yuriy Khachaturian (yukh.net)", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onCheckUpdates, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Check for updates"))
                    }
                }
            }
        }
    }

    if (showSetPasscode) {
        SetPasscodeDialog(
            onConfirm = { pin ->
                vm.setPasscode(pin)
                hasPasscode = true
                showSetPasscode = false
            },
            onDismiss = { showSetPasscode = false },
        )
    }
}

@Composable
private fun SetPasscodeDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length in 4..8 && pin == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Set passcode")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Set a 4–8 digit passcode"), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                    label = { Text(tr("Passcode")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter(Char::isDigit).take(8) },
                    label = { Text(tr("Confirm passcode")) },
                    singleLine = true,
                    isError = confirm.isNotEmpty() && confirm != pin,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }, enabled = valid) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun SettingRow(
    title: String,
    titleColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    onClick: () -> Unit,
) {
    Text(
        title,
        color = titleColor,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}
