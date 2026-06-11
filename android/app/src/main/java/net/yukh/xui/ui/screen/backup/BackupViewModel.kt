package net.yukh.xui.ui.screen.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.yukh.xui.data.repo.DbBackup
import net.yukh.xui.data.repo.PanelRepository

data class BackupUiState(
    val busy: Boolean = false,
    /** Downloaded backup awaiting a "save to" location chosen by the user. */
    val pendingBackup: DbBackup? = null,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Backup / restore of the whole panel database (settings, inbounds, clients and
 * the Xray config all live in it). Engine-agnostic: the panel returns x-ui.db
 * (SQLite) or x-ui.dump (PostgreSQL) and imports whichever back under its own
 * engine. File I/O is done by the screen (it owns the Context / SAF pickers);
 * this VM only does the network round-trip.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Download the DB; on success the screen opens a "save to" picker seeded
     *  with the panel-chosen filename. */
    fun startBackup() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            repo.downloadDb()
                .onSuccess { db -> _state.update { it.copy(busy = false, pendingBackup = db) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Backup failed") } }
        }
    }

    /** The screen calls this once the picked location has been written (or the
     *  picker was cancelled / the write failed). */
    fun onBackupConsumed(message: String?) {
        _state.update { it.copy(pendingBackup = null, message = message) }
    }

    /** Upload a chosen backup file to restore the panel (restarts Xray). */
    fun restore(filename: String, bytes: ByteArray) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            repo.importDb(filename, bytes)
                .onSuccess { _state.update { it.copy(busy = false, message = "Restored — Xray restarted") } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Restore failed") } }
        }
    }

    fun reportError(message: String) = _state.update { it.copy(error = message) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun dismissError() = _state.update { it.copy(error = null) }
}
