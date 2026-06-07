package net.yukh.xui.ui.screen.inbounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.data.repo.PanelRepository

data class InboundsUiState(
    val items: List<InboundSlim> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val toggleInFlight: Set<Int> = emptySet(),
    val transientMessage: String? = null,
)

@HiltViewModel
class InboundsViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InboundsUiState())
    val state: StateFlow<InboundsUiState> = _state.asStateFlow()

    fun load(force: Boolean = false) {
        val current = _state.value
        if (current.refreshing) return
        val firstLoad = current.items.isEmpty() && current.error == null
        _state.update {
            it.copy(
                refreshing = true,
                loading = it.loading || firstLoad,
                error = if (force) null else it.error,
            )
        }
        viewModelScope.launch {
            repo.listInboundsSlim()
                .onSuccess { list ->
                    _state.update {
                        it.copy(
                            items = list.sortedBy { ib -> ib.id },
                            loading = false,
                            refreshing = false,
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, refreshing = false, error = e.message)
                    }
                }
        }
    }

    /**
     * Optimistic flip: update local state first, call the API, revert on failure.
     * The user gets immediate feedback for the common (success) case and a clear
     * snackbar message for the rare (failure) case.
     */
    fun toggle(id: Int, target: Boolean) {
        if (id in _state.value.toggleInFlight) return
        val previous = _state.value.items
        _state.update {
            it.copy(
                items = it.items.map { ib -> if (ib.id == id) ib.copy(enable = target) else ib },
                toggleInFlight = it.toggleInFlight + id,
            )
        }
        viewModelScope.launch {
            repo.setInboundEnable(id, target)
                .onSuccess {
                    _state.update {
                        it.copy(
                            toggleInFlight = it.toggleInFlight - id,
                            transientMessage = if (target) "Inbound enabled" else "Inbound disabled",
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            items = previous,
                            toggleInFlight = it.toggleInFlight - id,
                            transientMessage = "Toggle failed: ${e.message}",
                        )
                    }
                }
        }
    }

    fun dismissMessage() = _state.update { it.copy(transientMessage = null) }
}
