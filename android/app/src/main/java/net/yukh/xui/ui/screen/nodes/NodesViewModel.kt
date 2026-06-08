package net.yukh.xui.ui.screen.nodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.yukh.xui.data.api.dto.Node
import net.yukh.xui.data.api.dto.NodeModel
import net.yukh.xui.data.repo.PanelRepository

data class NodesUiState(
    val items: List<Node> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val transientMessage: String? = null,
    val editor: NodeEditorState? = null,
    // Latest 3x-ui version (from the central panel) — a node is "outdated" when
    // its panelVersion differs. Empty until fetched.
    val latestVersion: String = "",
    val updatingIds: Set<Int> = emptySet(),
)

data class NodeEditorState(
    val isNew: Boolean,
    val id: Int = 0,
    val name: String = "",
    val remark: String = "",
    val scheme: String = "https",
    val address: String = "",
    val port: String = "443",
    val basePath: String = "/",
    val apiToken: String = "",
    val enable: Boolean = true,
    val allowPrivateAddress: Boolean = false,
    val tlsVerifyMode: String = "verify",
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = !saving && name.isNotBlank() && address.isNotBlank() &&
            (port.toIntOrNull() ?: 0) in 1..65535 && apiToken.isNotBlank()
}

@HiltViewModel
class NodesViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NodesUiState())
    val state: StateFlow<NodesUiState> = _state.asStateFlow()

    fun load(force: Boolean = false) {
        if (_state.value.refreshing) return
        val firstLoad = _state.value.items.isEmpty() && _state.value.error == null
        _state.update { it.copy(refreshing = true, loading = it.loading || firstLoad, error = if (force) null else it.error) }
        viewModelScope.launch {
            repo.listNodes()
                .onSuccess { list ->
                    _state.update { it.copy(items = list.sortedBy { n -> n.id }, loading = false, refreshing = false, error = null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, refreshing = false, error = e.message) }
                }
        }
        // Latest version (to flag outdated nodes). Fetched once; cheap to skip if known.
        if (_state.value.latestVersion.isBlank()) {
            viewModelScope.launch {
                repo.getPanelUpdateInfo().onSuccess { info ->
                    _state.update { it.copy(latestVersion = info.latestVersion.removePrefix("v")) }
                }
            }
        }
    }

    /** Trigger a 3x-ui self-update on a single node via the central panel. */
    fun updateNode(id: Int) {
        if (id in _state.value.updatingIds) return
        _state.update { it.copy(updatingIds = it.updatingIds + id) }
        viewModelScope.launch {
            repo.updateNodes(listOf(id))
                .onSuccess { _state.update { it.copy(transientMessage = "Node update started") } }
                .onFailure { e -> _state.update { it.copy(transientMessage = "Node update failed: ${e.message}") } }
            // The node restarts during the update; refresh a bit later, then clear the flag.
            kotlinx.coroutines.delay(4000)
            _state.update { it.copy(updatingIds = it.updatingIds - id) }
            load(force = true)
        }
    }

    fun openCreateEditor() = _state.update { it.copy(editor = NodeEditorState(isNew = true)) }

    fun openEditEditor(id: Int) {
        val n = _state.value.items.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                editor = NodeEditorState(
                    isNew = false,
                    id = n.id,
                    name = n.name,
                    remark = n.remark,
                    scheme = n.scheme,
                    address = n.address,
                    port = n.port.toString(),
                    basePath = n.basePath,
                    apiToken = n.apiToken,
                    enable = n.enable,
                    allowPrivateAddress = n.allowPrivateAddress,
                    tlsVerifyMode = n.tlsVerifyMode,
                ),
            )
        }
    }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    private fun edit(t: (NodeEditorState) -> NodeEditorState) {
        _state.update { s -> s.editor?.let { s.copy(editor = t(it).copy(error = null)) } ?: s }
    }

    fun setName(v: String) = edit { it.copy(name = v) }
    fun setRemark(v: String) = edit { it.copy(remark = v) }
    fun setScheme(v: String) = edit { it.copy(scheme = v) }
    fun setAddress(v: String) = edit { it.copy(address = v) }
    fun setPort(v: String) = edit { it.copy(port = v.filter(Char::isDigit)) }
    fun setBasePath(v: String) = edit { it.copy(basePath = v) }
    fun setApiToken(v: String) = edit { it.copy(apiToken = v) }
    fun setEnable(v: Boolean) = edit { it.copy(enable = v) }
    fun setAllowPrivate(v: Boolean) = edit { it.copy(allowPrivateAddress = v) }
    fun setTlsVerifyMode(v: String) = edit { it.copy(tlsVerifyMode = v) }

    fun saveEditor() {
        val e = _state.value.editor ?: return
        if (!e.canSave) return
        val model = NodeModel(
            id = e.id,
            name = e.name.trim(),
            remark = e.remark.trim(),
            scheme = e.scheme,
            address = e.address.trim(),
            port = e.port.toIntOrNull() ?: 443,
            basePath = e.basePath.trim().ifBlank { "/" },
            apiToken = e.apiToken.trim(),
            enable = e.enable,
            allowPrivateAddress = e.allowPrivateAddress,
            tlsVerifyMode = e.tlsVerifyMode,
        )
        _state.update { s -> s.editor?.let { s.copy(editor = it.copy(saving = true, error = null)) } ?: s }
        viewModelScope.launch {
            val result = if (e.isNew) repo.addNode(model) else repo.updateNode(e.id, model)
            result
                .onSuccess {
                    _state.update { it.copy(editor = null, transientMessage = if (e.isNew) "Node added" else "Node updated") }
                    load(force = true)
                }
                .onFailure { ex ->
                    _state.update { s -> s.editor?.let { s.copy(editor = it.copy(saving = false, error = ex.message)) } ?: s }
                }
        }
    }

    fun deleteNode(id: Int) {
        viewModelScope.launch {
            repo.deleteNode(id)
                .onSuccess {
                    _state.update { it.copy(editor = null, items = it.items.filterNot { n -> n.id == id }, transientMessage = "Node deleted") }
                }
                .onFailure { e -> _state.update { it.copy(transientMessage = "Delete failed: ${e.message}") } }
        }
    }

    fun dismissMessage() = _state.update { it.copy(transientMessage = null) }
}
