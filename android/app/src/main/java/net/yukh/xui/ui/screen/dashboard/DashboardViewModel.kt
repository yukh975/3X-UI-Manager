package net.yukh.xui.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    // Online list dialog — grouped by server (main panel + each node)
    val showOnlineList: Boolean = false,
    val onlineLoading: Boolean = false,
    val onlineGroups: List<OnlineGroup> = emptyList(),
    // Panel update
    val updateInfo: PanelUpdateInfo? = null,
    val updating: Boolean = false,
    val updateMessage: String? = null,
) {
    val onlineCount: Int get() = onlineEmails.size
}

/** One server's currently-online clients, for the grouped online dialog. */
data class OnlineGroup(
    val server: String,
    val isMain: Boolean,
    val emails: List<String>,
)

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

    // Shows who is online, grouped by SERVER (main panel first, then each node).
    // The central API can't say which inbound a client uses, but each node is its
    // own 3x-ui panel that reports its own онлайн — so querying every node gives a
    // true per-server breakdown (and reveals a client connected to several nodes
    // at once). Node queries run in parallel.
    fun openOnlineList() {
        _state.update {
            it.copy(
                showOnlineList = true,
                onlineLoading = true,
                onlineGroups = listOf(OnlineGroup("", isMain = true, emails = it.onlineEmails.sorted())),
            )
        }
        viewModelScope.launch {
            val nodes = repo.listNodes().getOrNull().orEmpty().filter { it.enable }
            val nodeGroups = nodes.map { node ->
                async {
                    val emails = repo.listNodeOnlines(node).getOrNull().orEmpty().sorted()
                    OnlineGroup(node.remark.ifBlank { node.name }, isMain = false, emails = emails)
                }
            }.awaitAll()
            val main = OnlineGroup("", isMain = true, emails = _state.value.onlineEmails.sorted())
            _state.update { it.copy(onlineGroups = listOf(main) + nodeGroups, onlineLoading = false) }
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

    // Only Restart is exposed. There is deliberately NO Start/Stop: panels are
    // commonly reverse-proxied through Xray, so stopping Xray also cuts off the
    // panel/API (confirmed with both token and login/password sessions) and the
    // app can't bring it back — recovery needs a host-level panel restart.
    // restartXrayService is safe: Xray comes back up and the connection returns.
    fun restartXray() {
        if (_state.value.xrayActionInFlight) return
        _state.update { it.copy(xrayActionInFlight = true, xrayActionMessage = null) }
        viewModelScope.launch {
            repo.restartXray()
                .onSuccess {
                    _state.update {
                        it.copy(xrayActionInFlight = false, xrayActionMessage = "Xray restart requested")
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(xrayActionInFlight = false, xrayActionMessage = "Xray restart failed: ${e.message}")
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
