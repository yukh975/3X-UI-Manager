package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Full-screen lock gate: passcode entry + optional biometric unlock. */
@Composable
fun LockScreen(
    biometryEnabled: Boolean,
    onUnlock: (String) -> Boolean,
    onBiometric: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (biometryEnabled) onBiometric() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔒", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(tr("Enter passcode"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(8); wrong = false },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = wrong,
            modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp),
        )
        if (wrong) {
            Spacer(Modifier.height(8.dp))
            Text(tr("Wrong passcode"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (!onUnlock(code)) { wrong = true; code = "" } },
            enabled = code.length >= 4,
            modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp),
        ) { Text(tr("Unlock")) }
        if (biometryEnabled) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBiometric) { Text(tr("Use biometrics")) }
        }
    }
}
