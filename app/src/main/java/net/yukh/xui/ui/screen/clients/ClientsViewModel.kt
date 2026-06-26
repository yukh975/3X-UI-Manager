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
import net.yukh.xui.data.api.dto.ClientModel
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.data.repo.PanelRepository

private const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0

data class ClientsUiState(
    val items: List<Client> = emptyList(),
    val online: Set<String> = emptySet(),
    val searchQuery: String = "",
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val transientMessage: String? = null,
    val selectedClientEmail: String? = null,
    val selectedLinks: List<String> = emptyList(),
    val linksLoading: Boolean = false,
    val linksError: String? = null,
    val selectedSubUrl: String? = null,
    val subUrlChecked: Boolean = false,
    val filters: ClientFilters = ClientFilters(),
    val editor: ClientEditorState? = null,
) {
    /** Distinct non-empty client groups in use, sorted — for the editor picker
     *  and the filter sheet. */
    val allGroups: List<String>
        get() = items.map { it.group.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedBy { it.lowercase() }

    /** Items narrowed by the search query (case-insensitive email substring) and
     *  the active status/group [filters]. */
    val visibleItems: List<Client>
        get() {
            val q = searchQuery.trim()
            val now = System.currentTimeMillis()
            return items.filter { c ->
                (q.isEmpty() || c.email.contains(q, ignoreCase = true)) &&
                    c.matches(filters, now, online)
            }
        }
}

/** Form state for creating or editing a client. Numeric inputs are kept as
 *  strings so partial/empty typing doesn't fight the user. */
data class ClientEditorState(
    val isNew: Boolean,
    val source: Client? = null,
    val email: String = "",
    val enable: Boolean = true,
    val limitIp: String = "0",
    val totalGb: String = "0",
    val expiryTime: Long = 0,
    val reset: String = "0",
    val tgId: String = "",
    val group: String = "",
    val comment: String = "",
    val selectedInboundIds: Set<Int> = emptySet(),
    val availableInbounds: List<InboundSlim> = emptyList(),
    val availableGroups: List<String> = emptyList(),
    val inboundsLoading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = !saving && email.isNotBlank() && (!isNew || selectedInboundIds.isNotEmpty())
}

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
        val client = _state.value.items.firstOrNull { it.email == email }
        _state.update {
            it.copy(
                selectedClientEmail = email,
                selectedLinks = emptyList(),
                linksLoading = true,
                linksError = null,
                selectedSubUrl = null,
                subUrlChecked = false,
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
        // Subscription URL needs panel settings (token-readable on v3.3.0) —
        // fetch in parallel and degrade silently if unavailable (old panels).
        viewModelScope.launch {
            val subUrl = client?.let { repo.getSubscriptionUrl(it) }
            _state.update { it.copy(selectedSubUrl = subUrl, subUrlChecked = true) }
        }
    }

    fun closeShareSheet() {
        _state.update {
            it.copy(
                selectedClientEmail = null,
                selectedLinks = emptyList(),
                linksLoading = false,
                linksError = null,
                selectedSubUrl = null,
                subUrlChecked = false,
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
                            selectedLinks = emptyList(),
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

    // ---- Editor -----------------------------------------------------------

    /** Distinct non-empty client groups already in use, for the editor's picker. */
    private fun existingGroups(): List<String> = _state.value.allGroups

    fun openCreateEditor() {
        _state.update {
            it.copy(editor = ClientEditorState(isNew = true, inboundsLoading = true, availableGroups = existingGroups()))
        }
        loadInboundsForEditor()
    }

    fun openEditEditor(email: String) {
        val client = _state.value.items.firstOrNull { it.email == email } ?: return
        val gb = if (client.totalGB > 0) {
            (client.totalGB / BYTES_PER_GB).let { v -> if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(v) }
        } else "0"
        _state.update {
            it.copy(
                selectedClientEmail = null, // close share sheet if open
                editor = ClientEditorState(
                    isNew = false,
                    source = client,
                    email = client.email,
                    enable = client.enable,
                    limitIp = client.limitIp.toString(),
                    totalGb = gb,
                    expiryTime = client.expiryTime,
                    reset = client.reset.toString(),
                    tgId = if (client.tgId != 0L) client.tgId.toString() else "",
                    group = client.group,
                    comment = client.comment,
                    selectedInboundIds = client.inboundIds.toSet(),
                    availableGroups = existingGroups(),
                    inboundsLoading = true,
                ),
            )
        }
        loadInboundsForEditor()
    }

    private fun loadInboundsForEditor() {
        viewModelScope.launch {
            val inbounds = repo.listInbounds().getOrNull().orEmpty()
            _state.update { s ->
                s.editor?.let { e ->
                    s.copy(editor = e.copy(availableInbounds = inbounds, inboundsLoading = false))
                } ?: s
            }
        }
    }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    private fun updateEditor(transform: (ClientEditorState) -> ClientEditorState) {
        _state.update { s -> s.editor?.let { s.copy(editor = transform(it).copy(error = null)) } ?: s }
    }

    fun setEditorEmail(v: String) = updateEditor { it.copy(email = v) }
    fun setEditorEnable(v: Boolean) = updateEditor { it.copy(enable = v) }
    fun setEditorLimitIp(v: String) = updateEditor { it.copy(limitIp = v.filter(Char::isDigit)) }
    fun setEditorTotalGb(v: String) = updateEditor { it.copy(totalGb = v.filter { c -> c.isDigit() || c == '.' }) }
    fun setEditorReset(v: String) = updateEditor { it.copy(reset = v.filter(Char::isDigit)) }
    fun setEditorTgId(v: String) = updateEditor { it.copy(tgId = v.filter(Char::isDigit)) }
    fun setEditorGroup(v: String) = updateEditor { it.copy(group = v) }
    fun setEditorComment(v: String) = updateEditor { it.copy(comment = v) }
    fun setEditorExpiry(ms: Long) = updateEditor { it.copy(expiryTime = ms) }
    fun toggleEditorInbound(id: Int) = updateEditor {
        it.copy(
            selectedInboundIds = if (id in it.selectedInboundIds) it.selectedInboundIds - id
            else it.selectedInboundIds + id,
        )
    }

    fun saveEditor() {
        val e = _state.value.editor ?: return
        if (!e.canSave) return
        val gbBytes = (e.totalGb.toDoubleOrNull() ?: 0.0).let { (it * BYTES_PER_GB).toLong() }
        val base = e.source?.toModel() ?: ClientModel()
        val model = base.copy(
            email = e.email.trim(),
            enable = e.enable,
            limitIp = e.limitIp.toIntOrNull() ?: 0,
            totalGB = gbBytes,
            expiryTime = e.expiryTime,
            reset = e.reset.toIntOrNull() ?: 0,
            tgId = e.tgId.toLongOrNull() ?: 0,
            group = e.group.trim(),
            comment = e.comment.trim(),
        )
        _state.update { s -> s.editor?.let { s.copy(editor = it.copy(saving = true, error = null)) } ?: s }
        viewModelScope.launch {
            val result = if (e.isNew) {
                repo.addClient(model, e.selectedInboundIds.toList())
            } else {
                // Update by the ORIGINAL email (the lookup key); model.email carries
                // the possibly-renamed value. Then reconcile inbound membership.
                val origEmail = e.source?.email ?: e.email.trim()
                val newEmail = model.email
                repo.updateClient(origEmail, model).also { r ->
                    if (r.isSuccess) {
                        val original = e.source?.inboundIds?.toSet().orEmpty()
                        val selected = e.selectedInboundIds
                        val added = (selected - original).toList()
                        val removed = (original - selected).toList()
                        if (added.isNotEmpty()) repo.attachClient(newEmail, added)
                        if (removed.isNotEmpty()) repo.detachClient(newEmail, removed)
                    }
                }
            }
            result
                .onSuccess {
                    _state.update {
                        it.copy(
                            editor = null,
                            transientMessage = if (e.isNew) "Client created" else "Client updated",
                        )
                    }
                    load(force = true)
                }
                .onFailure { ex ->
                    _state.update { s -> s.editor?.let { s.copy(editor = it.copy(saving = false, error = ex.message)) } ?: s }
                }
        }
    }

    fun setSearchQuery(q: String) = _state.update { it.copy(searchQuery = q) }

    // ---- Filters ----------------------------------------------------------

    fun toggleStatusFilter(s: ClientStatus) = _state.update {
        val cur = it.filters.statuses
        it.copy(filters = it.filters.copy(statuses = if (s in cur) cur - s else cur + s))
    }

    fun toggleGroupFilter(g: String) = _state.update {
        val cur = it.filters.groups
        it.copy(filters = it.filters.copy(groups = if (g in cur) cur - g else cur + g))
    }

    fun clearFilters() = _state.update { it.copy(filters = ClientFilters()) }

    fun dismissMessage() = _state.update { it.copy(transientMessage = null) }
}
