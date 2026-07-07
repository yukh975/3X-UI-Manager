package net.yukh.xui.ui.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.yukh.xui.BuildConfig
import net.yukh.xui.i18n.LANG_EN
import net.yukh.xui.i18n.LANG_RU
import net.yukh.xui.i18n.tr
import net.yukh.xui.security.BiometricAuth
import net.yukh.xui.work.AlertScheduler
import androidx.compose.material3.OutlinedButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onCheckUpdates: () -> Unit = {},
    showAppLock: Boolean = true,
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

    var alerts by remember { mutableStateOf(vm.alertsEnabled()) }
    var expiryDays by remember { mutableStateOf(vm.alertExpiryDays().toString()) }
    var trafficPct by remember { mutableStateOf(vm.alertTrafficPct().toString()) }
    var panelPort by remember { mutableStateOf(vm.alertPanelPort().toString()) }
    fun startAlerts() {
        vm.setAlertsEnabled(true)
        alerts = true
        AlertScheduler.ensureScheduled(context)
        AlertScheduler.runNow(context) // immediate first pass — instant feedback
    }
    // Android 13+ needs the runtime notification permission; enable on the
    // way back regardless of the answer (the OS just won't show them if denied).
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { startAlerts() }
    fun enableAlerts() {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startAlerts()
    }

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

            // ---- App lock (hidden before sign-in; the lock guards the panel UI) ----
            if (showAppLock) {
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
            }

            // ---- Panel alerts ----
            Text(tr("Notifications"), style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("Panel alerts"), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                tr("Client expiry, traffic limits, inbounds & node status"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = alerts,
                            onCheckedChange = { on ->
                                if (on) {
                                    enableAlerts()
                                } else {
                                    vm.setAlertsEnabled(false)
                                    alerts = false
                                    AlertScheduler.cancel(context)
                                }
                            },
                        )
                    }
                    if (alerts) {
                        HorizontalDivider()
                        OutlinedTextField(
                            value = panelPort,
                            onValueChange = { raw ->
                                val t = raw.filter(Char::isDigit).take(5)
                                panelPort = t
                                t.toIntOrNull()?.takeIf { it in 1..65535 }?.let(vm::setAlertPanelPort)
                            },
                            label = { Text(tr("Panel port")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 12.dp),
                        )
                        Text(
                            tr("This port on the panel host is probed for reachability (default 443). Per-inbound ports are monitored separately, from each inbound's editor."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 24.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = expiryDays,
                                onValueChange = { raw ->
                                    val t = raw.filter(Char::isDigit).take(3)
                                    expiryDays = t
                                    t.toIntOrNull()?.takeIf { it in 1..365 }
                                        ?.let(vm::setAlertExpiryDays)
                                },
                                label = { Text(tr("Days before expiry")) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = trafficPct,
                                onValueChange = { raw ->
                                    val t = raw.filter(Char::isDigit).take(3)
                                    trafficPct = t
                                    t.toIntOrNull()?.takeIf { it in 1..100 }
                                        ?.let(vm::setAlertTrafficPct)
                                },
                                label = { Text(tr("Traffic threshold (%)")) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            tr("Checks all saved panels every 30 minutes."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp),
                        )
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
                    // F-Droid ships its own updater, so hide the in-app check there.
                    if (BuildConfig.IN_APP_UPDATER) {
                        OutlinedButton(onClick = onCheckUpdates, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("Check for updates"))
                        }
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
