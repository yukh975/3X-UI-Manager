package net.yukh.xui.ui.screen.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.yukh.xui.data.api.dto.Client
import net.yukh.xui.data.api.dto.ClientLinks
import net.yukh.xui.data.repo.PanelRepository

data class ClientsUiState(
    val items: List<Client> = emptyList(),
    val online: Set<String> = emptySet(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val transientMessage: String? = null,
    val selectedClientEmail: String? = null,
    val selectedLinks: ClientLinks? = null,
    val linksLoading: Boolean = false,
    val linksError: String? = null,
)

@HiltViewModel
class ClientsViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ClientsUiState())
    val state: StateFlow<ClientsUiState> = _state.asStateFlow()

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
            val clients = repo.listClients()
            val onlines = repo.listOnlines()

            clients
                .onSuccess { list ->
                    _state.update {
                        it.copy(
                            items = list.sortedBy { c -> c.email.lowercase() },
                            online = onlines.getOrNull()?.toSet().orEmpty(),
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

    fun openShareSheet(email: String) {
        _state.update {
            it.copy(
                selectedClientEmail = email,
                selectedLinks = null,
                linksLoading = true,
                linksError = null,
            )
        }
        viewModelScope.launch {
            repo.getClientLinks(email)
                .onSuccess { links ->
                    _state.update { it.copy(selectedLinks = links, linksLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(linksLoading = false, linksError = e.message) }
                }
        }
    }

    fun closeShareSheet() {
        _state.update {
            it.copy(
                selectedClientEmail = null,
                selectedLinks = null,
                linksLoading = false,
                linksError = null,
            )
        }
    }

    fun deleteClient(email: String) {
        viewModelScope.launch {
            repo.deleteClient(email)
                .onSuccess {
                    _state.update {
                        it.copy(
                            items = it.items.filterNot { c -> c.email == email },
                            transientMessage = "Client $email deleted",
                            selectedClientEmail = null,
                            selectedLinks = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(transientMessage = "Delete failed: ${e.message}")
                    }
                }
        }
    }

    fun dismissMessage() = _state.update { it.copy(transientMessage = null) }
}
