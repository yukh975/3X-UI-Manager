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
    val pullRefreshing: Boolean = false,
    val error: String? = null,
    val xrayActionInFlight: Boolean = false,
    val xrayActionMessage: String? = null,
    // True for login/password sessions; token sessions only get Restart (see
    // runXrayAction — stopping Xray can cut a proxied-through-Xray panel off).
    val sessionAuth: Boolean = false,
    // Online list dialog
    val showOnlineList: Boolean = false,
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
        _state.update { it.copy(loading = it.status == null, sessionAuth = repo.hasStoredCredentials()) }
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

    /** Manual pull-to-refresh: shows the pull indicator until the fetch returns. */
    fun onPullRefresh() {
        viewModelScope.launch {
            _state.update { it.copy(pullRefreshing = true) }
            fetchOnce()
            refreshUpdateInfo()
            _state.update { it.copy(pullRefreshing = false) }
        }
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

    // Just shows which clients are online (emails). We deliberately don't show
    // the inbound: 3x-ui keys online/traffic by email under a single canonical
    // inbound and replicates that record into every inbound the email belongs
    // to, so the API can't tell which inbound(s) a client is actually live on.
    fun openOnlineList() = _state.update { it.copy(showOnlineList = true) }

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

    // Start/Restart both call restartXrayService (starts Xray when down, restarts
    // when up). Stop uses stopXrayService and is only offered for login/password
    // sessions (see sessionAuth): on panels reverse-proxied through Xray, stopping
    // Xray also cuts off the panel/API, and a token session typically can't bring
    // it back — so token mode is restricted to Restart only.
    fun startXray() = runXrayAction(verb = "start", resultRunning = true) { repo.restartXray() }
    fun restartXray() = runXrayAction(verb = "restart", resultRunning = true) { repo.restartXray() }
    fun stopXray() = runXrayAction(verb = "stop", resultRunning = false) { repo.stopXray() }

    private fun runXrayAction(
        verb: String,
        resultRunning: Boolean,
        action: suspend () -> Result<Unit>,
    ) {
        if (_state.value.xrayActionInFlight) return
        _state.update { it.copy(xrayActionInFlight = true, xrayActionMessage = null) }
        viewModelScope.launch {
            val result = action()
            result
                .onSuccess {
                    // Reflect the new Xray state immediately so the right control
                    // shows even if the next poll never returns (e.g. a stop that
                    // cuts off a proxied-through-Xray panel).
                    val newState = if (resultRunning) "running" else "stop"
                    _state.update { st ->
                        st.copy(
                            xrayActionInFlight = false,
                            xrayActionMessage = "Xray $verb requested",
                            status = st.status?.let { it.copy(xray = it.xray.copy(state = newState)) },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            xrayActionInFlight = false,
                            xrayActionMessage = "Xray $verb failed: ${e.message}",
                        )
                    }
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
