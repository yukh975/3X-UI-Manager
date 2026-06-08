package net.yukh.xui.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.yukh.xui.data.api.dto.PanelUpdateInfo
import net.yukh.xui.data.api.dto.ServerStatus
import net.yukh.xui.data.repo.PanelRepository

data class DashboardUiState(
    val status: ServerStatus? = null,
    val onlineEmails: List<String> = emptyList(),
    val loading: Boolean = false,
    val refreshingNow: Boolean = false,
    val error: String? = null,
    val xrayActionInFlight: Boolean = false,
    val xrayActionMessage: String? = null,
    // Online list dialog
    val showOnlineList: Boolean = false,
    val emailToInbounds: Map<String, List<String>> = emptyMap(),
    // Panel update
    val updateInfo: PanelUpdateInfo? = null,
    val updating: Boolean = false,
    val updateMessage: String? = null,
) {
    val onlineCount: Int get() = onlineEmails.size
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun startPolling() {
        if (pollJob?.isActive == true) return
        _state.update { it.copy(loading = it.status == null) }
        if (_state.value.updateInfo == null) refreshUpdateInfo()
        pollJob = viewModelScope.launch {
            while (isActive) {
                fetchOnce()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refreshNow() {
        viewModelScope.launch { fetchOnce() }
    }

    private suspend fun fetchOnce() {
        _state.update { it.copy(refreshingNow = true) }
        val statusResult = repo.getServerStatus()
        val onlines = repo.listOnlines().getOrNull()
        statusResult
            .onSuccess { s ->
                _state.update {
                    it.copy(
                        status = s,
                        onlineEmails = onlines ?: it.onlineEmails,
                        loading = false,
                        refreshingNow = false,
                        error = null,
                    )
                }
            }
            .onFailure { e ->
                _state.update { it.copy(loading = false, refreshingNow = false, error = e.message) }
            }
    }

    // ---- Online list ------------------------------------------------------

    fun openOnlineList() {
        _state.update { it.copy(showOnlineList = true) }
        // Fetch inbound membership once so each online client shows its inbound.
        viewModelScope.launch {
            val inbounds = repo.listInbounds().getOrNull().orEmpty()
            val map = mutableMapOf<String, MutableList<String>>()
            inbounds.forEach { ib ->
                val name = ib.remark.ifBlank { "#${ib.id}" }
                ib.clientStats.forEach { c ->
                    if (c.email.isNotBlank()) map.getOrPut(c.email) { mutableListOf() }.add(name)
                }
            }
            _state.update { it.copy(emailToInbounds = map) }
        }
    }

    fun closeOnlineList() = _state.update { it.copy(showOnlineList = false) }

    // ---- Panel update -----------------------------------------------------

    private fun refreshUpdateInfo() {
        viewModelScope.launch {
            repo.getPanelUpdateInfo().onSuccess { info -> _state.update { it.copy(updateInfo = info) } }
        }
    }

    fun updatePanel() {
        if (_state.value.updating) return
        _state.update { it.copy(updating = true, updateMessage = null) }
        viewModelScope.launch {
            val result = repo.updatePanel()
            _state.update {
                it.copy(
                    updating = false,
                    updateMessage = result.fold(
                        onSuccess = { "Panel update started — it will restart shortly" },
                        onFailure = { e -> "Update failed: ${e.message}" },
                    ),
                )
            }
        }
    }

    fun dismissUpdateMessage() = _state.update { it.copy(updateMessage = null) }

    // ---- Xray controls ----------------------------------------------------

    fun restartXray() {
        if (_state.value.xrayActionInFlight) return
        _state.update { it.copy(xrayActionInFlight = true, xrayActionMessage = null) }
        viewModelScope.launch {
            val result = repo.restartXray()
            _state.update {
                it.copy(
                    xrayActionInFlight = false,
                    xrayActionMessage = result.fold(
                        onSuccess = { "Xray restart requested" },
                        onFailure = { e -> "Restart failed: ${e.message}" },
                    ),
                )
            }
            refreshNow()
        }
    }

    fun dismissActionMessage() = _state.update { it.copy(xrayActionMessage = null) }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 3_000L
    }
}
