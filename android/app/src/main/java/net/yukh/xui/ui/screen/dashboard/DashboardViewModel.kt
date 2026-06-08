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

    // After a start/stop/restart, the panel keeps reporting the OLD Xray state
    // for a beat (Xray hasn't settled yet). Without this, the immediate refresh
    // would overwrite our optimistic state and the button would flip back for ~3s.
    // So we pin the expected state for a short window; polls update everything
    // else but keep this Xray state until Xray actually settles.
    private var xrayOverrideState: String? = null
    private var xrayOverrideUntil: Long = 0L

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
            .onSuccess { raw ->
                val s = applyXrayOverride(raw)
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

    /** While the post-action window is open, force the Xray state we expect so a
     *  lagging poll doesn't flip the button back. */
    private fun applyXrayOverride(s: ServerStatus): ServerStatus {
        val want = xrayOverrideState ?: return s
        return if (System.currentTimeMillis() < xrayOverrideUntil) {
            s.copy(xray = s.xray.copy(state = want))
        } else {
            xrayOverrideState = null
            s
        }
    }

    // ---- Online list ------------------------------------------------------

    // Shows who is online, grouped by SERVER (main panel first, then each node).
    // The central API can't say which inbound a client uses, but each node is its
    // own 3x-ui panel that reports its own онлайн — so querying every node gives a
    // true per-server breakdown (and reveals a client connected to several nodes
    // at once). Node queries run in parallel.
    fun openOnlineList() {
        _state.update { it.copy(showOnlineList = true, onlineLoading = true, onlineGroups = emptyList()) }
        viewModelScope.launch {
            val online = _state.value.onlineEmails.toSet()

            // The central /clients/onlines returns online emails across the WHOLE
            // tree (main + every node), so it can't be used as-is for the "main
            // server" group — it would list node-only clients (e.g. an outbound
            // credential that's only a member of a node inbound). Attribute online
            // emails to a server by inbound ownership (nodeId): 0/absent = main.
            val inbounds = repo.listInbounds().getOrNull().orEmpty()
            fun membersOf(nodeId: Int): Set<String> =
                inbounds.filter { (it.nodeId ?: 0) == nodeId }
                    .flatMap { ib -> ib.clientStats.map { cs -> cs.email } }
                    .toSet()

            val main = OnlineGroup(
                "", isMain = true,
                emails = online.filter { it in membersOf(0) }.sorted(),
            )

            val nodes = repo.listNodes().getOrNull().orEmpty().filter { it.enable }
            val nodeGroups = nodes.map { node ->
                async {
                    // Prefer the node's own live "who's connected to me" list. If the
                    // node isn't directly reachable from the device, fall back to
                    // membership so its online clients aren't silently dropped.
                    val direct = repo.listNodeOnlines(node)
                    val emails = if (direct.isSuccess) {
                        direct.getOrNull().orEmpty()
                    } else {
                        online.filter { it in membersOf(node.id) }
                    }
                    OnlineGroup(node.remark.ifBlank { node.name }, isMain = false, emails = emails.sorted())
                }
            }.awaitAll()
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

    // Start/Restart both call restartXrayService (starts Xray when down, restarts
    // when up); Stop uses stopXrayService. NOTE: if the panel is reached *through*
    // Xray (reverse-proxied), stopping Xray cuts off the panel/API and the app
    // can't bring it back. With a direct (non-proxied) connection that's not an
    // issue — so all three are offered and the user picks what's safe for their
    // setup.
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
            action()
                .onSuccess {
                    // Reflect the new state immediately AND pin it for a few
                    // seconds so the forced refresh below (and the next poll)
                    // can't flip the button back while Xray is still settling.
                    val newState = if (resultRunning) "running" else "stop"
                    xrayOverrideState = newState
                    xrayOverrideUntil = System.currentTimeMillis() + XRAY_OVERRIDE_MS
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
                        it.copy(xrayActionInFlight = false, xrayActionMessage = "Xray $verb failed: ${e.message}")
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
        // How long after a start/stop/restart to trust the optimistic Xray state
        // over polled status (enough for Xray to actually settle).
        const val XRAY_OVERRIDE_MS = 6_000L
    }
}
