package net.yukh.xui.ui.screen.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.yukh.xui.data.repo.DbBackup
import net.yukh.xui.i18n.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onClose: () -> Unit, vm: BackupViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Translated once here (tr() is composable-only) so the file-I/O callbacks
    // below — which run outside composition — can still localise their result.
    val msgSaved = tr("Backup saved")
    val msgWriteFailed = tr("Couldn't write the backup file")
    val msgReadFailed = tr("Couldn't read the selected file")

    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() } }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }

    // Backup: once the DB is downloaded, open a "save to" picker seeded with the
    // panel-chosen filename, then write the bytes to the chosen location.
    var saving by remember { mutableStateOf<DbBackup?>(null) }
    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        val db = saving
        saving = null
        if (uri == null || db == null) {
            vm.onBackupConsumed(null)
        } else {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        ctx.contentResolver.openOutputStream(uri)?.use { it.write(db.bytes) } ?: error("no stream")
                    }.isSuccess
                }
                vm.onBackupConsumed(if (ok) msgSaved else msgWriteFailed)
            }
        }
    }
    LaunchedEffect(state.pendingBackup) {
        val db = state.pendingBackup ?: return@LaunchedEffect
        saving = db
        createDoc.launch(db.filename)
    }

    // Restore: pick a file, read it off the main thread, then confirm before
    // uploading (it overwrites the panel DB and restarts Xray).
    var confirm by remember { mutableStateOf<RestorePick?>(null) }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val pick = withContext(Dispatchers.IO) {
                runCatching {
                    val name = displayName(ctx, uri) ?: "x-ui.db"
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("no stream")
                    RestorePick(name, bytes)
                }.getOrNull()
            }
            if (pick != null) confirm = pick else vm.reportError(msgReadFailed)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(tr("Backup / restore")) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close")) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar { Text(it.visuals.message) } } },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                tr("The backup is the panel's whole database — settings, inbounds, clients and the Xray config. The panel saves SQLite as x-ui.db and PostgreSQL as x-ui.dump; either restores back here."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Back up"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(tr("Download the database and save it to a file on this device."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = vm::startBackup, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        if (state.busy && state.pendingBackup == null) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(tr("Back up to file"))
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Restore"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(tr("Replace the panel database from a backup file. This overwrites everything and restarts Xray — every active connection drops briefly."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(
                        onClick = { openDoc.launch(arrayOf("*/*")) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("Restore from file")) }
                }
            }

            if (state.busy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
    }

    confirm?.let { pick ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(tr("Restore from this backup?")) },
            text = {
                Text(
                    tr("Importing") + " \"${pick.filename}\" " +
                        tr("overwrites the panel database and restarts Xray. This can't be undone."),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val p = pick
                        confirm = null
                        vm.restore(p.filename, p.bytes)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(tr("Restore")) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("Cancel")) } },
        )
    }
}

private class RestorePick(val filename: String, val bytes: ByteArray)

private fun displayName(ctx: Context, uri: Uri): String? =
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
    }
