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
import net.yukh.xui.data.api.dto.ServerStatus
import net.yukh.xui.data.repo.PanelRepository

data class DashboardUiState(
    val status: ServerStatus? = null,
    val onlineCount: Int = 0,
    val loading: Boolean = false,
    val refreshingNow: Boolean = false,
    val error: String? = null,
    val xrayActionInFlight: Boolean = false,
    val xrayActionMessage: String? = null,
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
        // Online client count isn't in /status on v3.x — derive it from the
        // onlines list. A failure here shouldn't blank the whole dashboard.
        val onlineCount = repo.listOnlines().getOrNull()?.size
        statusResult
            .onSuccess { s ->
                _state.update {
                    it.copy(
                        status = s,
                        onlineCount = onlineCount ?: it.onlineCount,
                        loading = false,
                        refreshingNow = false,
                        error = null,
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(loading = false, refreshingNow = false, error = e.message)
                }
            }
    }

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
