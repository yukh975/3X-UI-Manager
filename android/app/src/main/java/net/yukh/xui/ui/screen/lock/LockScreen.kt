package net.yukh.xui.ui.screen.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.fragment.app.FragmentActivity
import net.yukh.xui.i18n.tr
import net.yukh.xui.security.BiometricAuth
import net.yukh.xui.security.LockState

@Composable
fun LockScreen(
    lockState: LockState,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    val biometricUsable = activity != null &&
        lockState.isBiometricEnabled() &&
        BiometricAuth.canAuthenticate(context)

    fun promptBiometric() {
        if (activity == null) return
        BiometricAuth.prompt(
            activity = activity,
            title = tr("Unlock"),
            negativeLabel = tr("Use passcode"),
            onSuccess = { lockState.unlock(); onUnlocked() },
            onError = {},
        )
    }

    LaunchedEffect(Unit) { if (biometricUsable) promptBiometric() }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                tr("Enter passcode"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
            )
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter(Char::isDigit).take(8)
                    error = false
                },
                label = { Text(tr("Passcode")) },
                singleLine = true,
                isError = error,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error) {
                Text(
                    tr("Wrong passcode"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Button(
                onClick = {
                    if (lockState.verify(pin)) {
                        lockState.unlock(); onUnlocked()
                    } else {
                        error = true
                    }
                },
                enabled = pin.length >= 4,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text(tr("Unlock")) }

            if (biometricUsable) {
                OutlinedButton(
                    onClick = { promptBiometric() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                    Text("  " + tr("Use fingerprint"))
                }
            }
        }
    }
}
