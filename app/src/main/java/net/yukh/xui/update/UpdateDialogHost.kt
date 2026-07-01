package net.yukh.xui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.yukh.xui.i18n.tr

/**
 * Renders the update dialog for whatever state [vm] is in (nothing when Idle).
 * Include once in a screen that owns an [UpdateViewModel].
 */
@Composable
fun UpdateDialogHost(vm: UpdateViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { vm.dismiss() },
            title = { Text(tr("Update available")) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${tr("Version")} ${s.release.version}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val notes = s.release.notes.trim()
                    if (notes.isNotBlank()) {
                        Text(notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.downloadAndInstall(s.release) }) { Text(tr("Update")) }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismiss() }) { Text(tr("Later")) }
            },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = { },
            title = { Text(tr("Downloading…")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { s.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${s.percent}%", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { },
        )

        UpdateState.Checking -> AlertDialog(
            onDismissRequest = { vm.dismiss() },
            title = { Text(tr("Checking for updates…")) },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
            confirmButton = { },
        )

        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = { vm.dismiss() },
            title = { Text(tr("You're up to date")) },
            text = { Text(tr("You already have the latest version.")) },
            confirmButton = { TextButton(onClick = { vm.dismiss() }) { Text(tr("OK")) } },
        )

        UpdateState.Error -> AlertDialog(
            onDismissRequest = { vm.dismiss() },
            title = { Text(tr("Update check failed")) },
            text = { Text(tr("Couldn't reach the update server. Try again later.")) },
            confirmButton = { TextButton(onClick = { vm.dismiss() }) { Text(tr("OK")) } },
        )

        UpdateState.Idle -> Unit
    }
}
