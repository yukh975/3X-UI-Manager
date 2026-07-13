package net.yukh.xui.ui.screen.xrayedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.data.api.dto.RouteTestResult
import net.yukh.xui.data.repo.PanelRepository

data class RuleEdit(val index: Int, val isNew: Boolean, val draft: JsonObject)
data class BalEdit(val index: Int, val isNew: Boolean, val draft: JsonObject)

data class RoutingUiState(
    val loading: Boolean = true,
    val available: Boolean = false,
    val config: JsonObject = JsonObject(emptyMap()),
    val testUrl: String = "https://www.google.com/generate_204",
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val editingRule: RuleEdit? = null,
    val editingBalancer: BalEdit? = null,
    // Route test (panel 3.5.0): probe which outbound a destination resolves to.
    val routeTesting: Boolean = false,
    val routeTestResult: RouteTestResult? = null,
    val routeTestError: String? = null,
    // Inbounds for the route tester's picker: (tag sent to the API, remark shown).
    val inboundOptions: List<Pair<String, String>> = emptyList(),
)

@HiltViewModel
class RoutingViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RoutingUiState())
    val state: StateFlow<RoutingUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val inbounds = repo.listInbounds()
            repo.loadXrayConfig()
                .onSuccess { (cfg, url) ->
                    val opts = inbounds.getOrNull().orEmpty()
                        .filter { it.tag.isNotBlank() }
                        .map { it.tag to it.remark }
                    _state.update { it.copy(loading = false, available = true, config = cfg, testUrl = url, inboundOptions = opts, dirty = false, error = null) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, available = false, error = e.message ?: "Xray config unavailable") } }
        }
    }

    fun update(config: JsonObject) = _state.update { it.copy(config = config, dirty = true) }

    fun openRule(index: Int, draft: JsonObject, isNew: Boolean) = _state.update { it.copy(editingRule = RuleEdit(index, isNew, draft)) }
    fun updateRuleDraft(d: JsonObject) = _state.update { it.copy(editingRule = it.editingRule?.copy(draft = d)) }
    fun closeRule() = _state.update { it.copy(editingRule = null) }

    fun openBalancer(index: Int, draft: JsonObject, isNew: Boolean) = _state.update { it.copy(editingBalancer = BalEdit(index, isNew, draft)) }
    fun updateBalDraft(d: JsonObject) = _state.update { it.copy(editingBalancer = it.editingBalancer?.copy(draft = d)) }
    fun closeBalancer() = _state.update { it.copy(editingBalancer = null) }

    fun save() {
        val st = _state.value
        if (st.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            repo.saveXrayConfig(st.config, st.testUrl)
                .onSuccess { _state.update { it.copy(saving = false, dirty = false, savedMessage = "Saved — restart Xray to apply") } }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message ?: "Save failed") } }
        }
    }

    /** Probe the live routing for [dest] (a domain or IP) as seen from
     *  [inboundTag]. Most rules key on the inbound, so without it the router
     *  can't decide and everything falls through to the default outbound.
     *  Tests the saved config on the server, so unsaved edits need a Save first. */
    fun testRoute(dest: String, inboundTag: String, network: String, port: String) {
        val d = dest.trim()
        if (d.isBlank() || _state.value.routeTesting) return
        val isIp = d.none { it.isLetter() } && (d.contains('.') || d.contains(':'))
        _state.update { it.copy(routeTesting = true, routeTestError = null, routeTestResult = null) }
        viewModelScope.launch {
            repo.testRoute(
                domain = if (isIp) "" else d,
                ip = if (isIp) d else "",
                port = port.trim(),
                network = network,
                inboundTag = inboundTag,
            )
                .onSuccess { r -> _state.update { it.copy(routeTesting = false, routeTestResult = r) } }
                .onFailure { e -> _state.update { it.copy(routeTesting = false, routeTestError = e.message ?: "test failed") } }
        }
    }

    fun clearRouteTest() = _state.update { it.copy(routeTestResult = null, routeTestError = null) }

    /** Show a one-off message in the snackbar (e.g. import result). */
    fun info(msg: String) = _state.update { it.copy(savedMessage = msg) }

    fun dismissSavedMessage() = _state.update { it.copy(savedMessage = null) }
    fun dismissError() = _state.update { it.copy(error = null) }
}
