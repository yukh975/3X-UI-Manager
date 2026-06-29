package net.yukh.xui.ui.screen.nodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.yukh.xui.data.api.dto.Node
import net.yukh.xui.data.api.dto.NodeModel
import net.yukh.xui.data.json.array
import net.yukh.xui.data.json.asObject
import net.yukh.xui.data.json.string
import net.yukh.xui.data.repo.PanelRepository
import net.yukh.xui.data.repo.ServerTraffic
import net.yukh.xui.ui.screen.xrayedit.loadXrayConfig

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
    // Proxied traffic this month per node id (Σ up+down of that node's inbounds).
    val trafficByNode: Map<Int, ServerTraffic> = emptyMap(),
    // Node mTLS dialog
    val mtlsOpen: Boolean = false,
    val mtlsCa: String? = null,
    val mtlsBusy: Boolean = false,
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
    val outboundTag: String = "",
    val availableOutboundTags: List<String> = emptyList(),
    // Inbounds currently hosted on this node — the panel blocks deletion while > 0.
    val inboundCount: Int = 0,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = !saving && name.isNotBlank() && address.isNotBlank() &&
            (port.toIntOrNull() ?: 0) in 1..65535 &&
            (apiToken.isNotBlank() || tlsVerifyMode == "mtls")
}

@HiltViewModel
class NodesViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NodesUiState())
    val state: StateFlow<NodesUiState> = _state.asStateFlow()

    init {
        // The Nodes screen doesn't poll, so reload it when the active panel
        // profile changes (the user switched panels).
        viewModelScope.launch {
            repo.activeProfileId.drop(1).collect { load(force = true) }
        }
    }

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
        // Per-node proxied traffic this month (one /inbounds/list call, grouped by nodeId).
        viewModelScope.launch {
            repo.monthlyTrafficByServer().onSuccess { byServer ->
                _state.update { it.copy(trafficByNode = byServer) }
            }
        }
    }

    /** Trigger a 3x-ui self-update on a single node via the central panel, then
     *  keep polling the node list until its reported version actually changes.
     *  The node downloads the new build and restarts — that takes far longer than
     *  one refresh, so a single reload used to fetch the still-old version (the
     *  status only updated after leaving to the Dashboard and back). */
    fun updateNode(id: Int, dev: Boolean = false) {
        if (id in _state.value.updatingIds) return
        val before = _state.value.items.firstOrNull { it.id == id }?.panelVersion
        _state.update { it.copy(updatingIds = it.updatingIds + id) }
        viewModelScope.launch {
            val started = repo.updateNodes(listOf(id), dev)
                .onSuccess { _state.update { it.copy(transientMessage = "Node update started") } }
                .onFailure { e -> _state.update { it.copy(transientMessage = "Node update failed: ${e.message}") } }
                .isSuccess
            if (!started) {
                _state.update { it.copy(updatingIds = it.updatingIds - id) }
                return@launch
            }
            // Re-poll until the node reports a new version (up to ~60 s). The card
            // stays in the "Updating…" state and the version refreshes in place.
            var changed = false
            var attempt = 0
            while (attempt < 15 && !changed) {
                kotlinx.coroutines.delay(4000)
                repo.listNodes().onSuccess { list ->
                    _state.update { it.copy(items = list.sortedBy { n -> n.id }) }
                    val now = list.firstOrNull { it.id == id }?.panelVersion
                    if (!now.isNullOrBlank() && now != before) changed = true
                }
                attempt++
            }
            _state.update {
                it.copy(
                    updatingIds = it.updatingIds - id,
                    transientMessage = if (changed) "Node updated" else "Update is taking a while — pull down to refresh",
                )
            }
        }
    }

    fun openCreateEditor() {
        _state.update { it.copy(editor = NodeEditorState(isNew = true)) }
        loadOutboundTags()
    }

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
                    outboundTag = n.outboundTag,
                    inboundCount = n.inboundCount,
                ),
            )
        }
        loadOutboundTags()
    }

    /** Outbound tags from the Xray config (excluding blackhole) for the
     *  "Connection outbound" picker. Degrades to empty if the config isn't readable. */
    private fun loadOutboundTags() {
        viewModelScope.launch {
            val tags = repo.loadXrayConfig().getOrNull()?.config?.array("outbounds")
                ?.map { it.asObject() }
                ?.filter { it.string("protocol") != "blackhole" }
                ?.map { it.string("tag") }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            edit { it.copy(availableOutboundTags = tags) }
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
    fun setOutboundTag(v: String) = edit { it.copy(outboundTag = v) }

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
            outboundTag = e.outboundTag.trim(),
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

    // ---- Node mTLS (panel v3.4.0) -----------------------------------------

    fun openMtls() {
        _state.update { it.copy(mtlsOpen = true, mtlsCa = null) }
        viewModelScope.launch {
            repo.nodeMtlsCa()
                .onSuccess { ca -> _state.update { it.copy(mtlsCa = ca) } }
                .onFailure { e -> _state.update { it.copy(transientMessage = "mTLS CA failed: ${e.message}") } }
        }
    }

    fun closeMtls() = _state.update { it.copy(mtlsOpen = false, mtlsCa = null) }

    fun saveMtlsTrustCa(ca: String) {
        _state.update { it.copy(mtlsBusy = true) }
        viewModelScope.launch {
            repo.setNodeMtlsTrustCA(ca.trim())
                .onSuccess { _state.update { it.copy(mtlsBusy = false, mtlsOpen = false, transientMessage = "Trusted CA saved — restart the panel to apply") } }
                .onFailure { e -> _state.update { it.copy(mtlsBusy = false, transientMessage = "Save failed: ${e.message}") } }
        }
    }

    fun dismissMessage() = _state.update { it.copy(transientMessage = null) }
}
