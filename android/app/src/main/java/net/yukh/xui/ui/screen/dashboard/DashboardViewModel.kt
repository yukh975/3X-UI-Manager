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

    fun openOnlineList() {
        _state.update { it.copy(showOnlineList = true) }
        // Resolve which inbound each online client is actually connected through.
        //
        // /onlines returns only emails, and the same email can be configured in
        // several inbounds (e.g. the same user offered over vless + vmess). The
        // panel doesn't say which one is live — but the connected inbound's
        // per-client `lastOnline` keeps updating while the others stay stale, so
        // we show only the inbound(s) with the most-recent lastOnline for that
        // email. If no lastOnline data is available we fall back to listing every
        // membership rather than hiding the info.
        viewModelScope.launch {
            val inbounds = repo.listInbounds().getOrNull().orEmpty()
            val byEmail = mutableMapOf<String, MutableList<Pair<String, Long>>>()
            inbounds.forEach { ib ->
                val name = ib.remark.ifBlank { "#${ib.id}" }
                ib.clientStats.forEach { c ->
                    if (c.email.isNotBlank()) {
                        byEmail.getOrPut(c.email) { mutableListOf() }.add(name to c.lastOnline)
                    }
                }
            }
            val map = byEmail.mapValues { (_, entries) ->
                val maxLast = entries.maxOf { it.second }
                if (maxLast <= 0L) {
                    entries.map { it.first }.distinct()
                } else {
                    entries.filter { it.second == maxLast }.map { it.first }.distinct()
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

    // restartXrayService also starts Xray when it's stopped, so "Start" and
    // "Restart" both hit it; "Stop" uses stopXrayService.
    fun startXray() = runXrayAction(start = true) { repo.restartXray() }
    fun restartXray() = runXrayAction(start = false) { repo.restartXray() }
    fun stopXray() = runXrayAction(start = false, stop = true) { repo.stopXray() }

    private fun runXrayAction(start: Boolean, stop: Boolean = false, action: suspend () -> Result<Unit>) {
        if (_state.value.xrayActionInFlight) return
        _state.update { it.copy(xrayActionInFlight = true, xrayActionMessage = null) }
        viewModelScope.launch {
            val result = action()
            val verb = when {
                stop -> "stop"
                start -> "start"
                else -> "restart"
            }
            _state.update {
                it.copy(
                    xrayActionInFlight = false,
                    xrayActionMessage = result.fold(
                        onSuccess = { "Xray $verb requested" },
                        onFailure = { e -> "Xray $verb failed: ${e.message}" },
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
