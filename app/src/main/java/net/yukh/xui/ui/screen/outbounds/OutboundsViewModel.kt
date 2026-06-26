package net.yukh.xui.ui.screen.outbounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.data.api.dto.defaultOutbound
import net.yukh.xui.data.api.dto.outboundTag
import net.yukh.xui.data.json.array
import net.yukh.xui.data.json.asObject
import net.yukh.xui.data.json.putArray
import net.yukh.xui.data.repo.PanelRepository

private val prettyJson = Json { prettyPrint = true; isLenient = true }

/** The outbound currently open in the editor. [index] = -1 for a new one. */
data class EditingOutbound(val index: Int, val isNew: Boolean, val draft: JsonObject)

data class OutboundsUiState(
    val loading: Boolean = true,
    val available: Boolean = false,
    val outbounds: List<JsonObject> = emptyList(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val editing: EditingOutbound? = null,
    val editorError: String? = null,
)

@HiltViewModel
class OutboundsViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OutboundsUiState())
    val state: StateFlow<OutboundsUiState> = _state.asStateFlow()

    // The full config, kept verbatim so siblings (routing/balancers/dns/…) survive.
    private var configObj: JsonObject = JsonObject(emptyMap())
    private var testUrl: String = "https://www.google.com/generate_204"

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.getXraySetting()
                .onSuccess { env ->
                    configObj = env.xraySetting.asObject()
                    testUrl = env.outboundTestUrl
                    val list = configObj.array("outbounds").map { it.asObject() }
                    _state.update {
                        it.copy(loading = false, available = true, outbounds = list, dirty = false, error = null)
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, available = false, error = e.message ?: "Xray config unavailable")
                    }
                }
        }
    }

    // ---- list actions (local until save) ----------------------------------

    fun openNew() = _state.update {
        it.copy(editing = EditingOutbound(-1, isNew = true, draft = defaultOutbound("vless", "")), editorError = null)
    }

    /** Open the editor pre-filled with an outbound parsed from a share link. */
    fun openImported(draft: JsonObject) = _state.update {
        it.copy(editing = EditingOutbound(-1, isNew = true, draft = draft), editorError = null)
    }

    fun openEdit(index: Int) = _state.update {
        val ob = it.outbounds.getOrNull(index) ?: return@update it
        it.copy(editing = EditingOutbound(index, isNew = false, draft = ob), editorError = null)
    }

    fun closeEditor() = _state.update { it.copy(editing = null, editorError = null) }

    fun updateDraft(draft: JsonObject) = _state.update {
        it.copy(editing = it.editing?.copy(draft = draft), editorError = null)
    }

    /** Validate the draft and commit it to the local list; keeps editor open on error. */
    fun applyDraft() {
        val st = _state.value
        val ed = st.editing ?: return
        val tag = ed.draft.outboundTag().trim()
        if (tag.isBlank()) {
            _state.update { it.copy(editorError = "Tag is required") }
            return
        }
        val clash = st.outbounds.withIndex().any { (i, ob) -> i != ed.index && ob.outboundTag() == tag }
        if (clash) {
            _state.update { it.copy(editorError = "Tag already used by another outbound") }
            return
        }
        val list = st.outbounds.toMutableList()
        if (ed.isNew) list.add(ed.draft) else list[ed.index] = ed.draft
        _state.update { it.copy(outbounds = list, dirty = true, editing = null, editorError = null) }
    }

    fun deleteAt(index: Int) = _state.update {
        val list = it.outbounds.toMutableList()
        if (index in list.indices) list.removeAt(index)
        it.copy(outbounds = list, dirty = true)
    }

    /** Move the outbound at [index] by [delta] (-1 up / +1 down). Order = priority. */
    fun move(index: Int, delta: Int) = _state.update {
        val list = it.outbounds.toMutableList()
        val to = index + delta
        if (index in list.indices && to in list.indices) {
            val item = list.removeAt(index)
            list.add(to, item)
            it.copy(outbounds = list, dirty = true)
        } else it
    }

    /** Append imported outbounds to the list (kept verbatim; saved on Save). */
    fun appendOutbounds(items: List<JsonObject>) = _state.update {
        it.copy(outbounds = it.outbounds + items, dirty = true)
    }

    // ---- persistence -------------------------------------------------------

    fun save() {
        val st = _state.value
        if (st.saving) return
        val updated = configObj.putArray("outbounds", st.outbounds)
        val minified = prettyJson.encodeToString(JsonElement.serializer(), updated)
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            repo.updateXraySetting(minified, testUrl.trim())
                .onSuccess {
                    configObj = updated
                    _state.update {
                        it.copy(saving = false, dirty = false, savedMessage = "Outbounds saved — restart Xray to apply")
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(saving = false, error = e.message ?: "Save failed") }
                }
        }
    }

    fun dismissSavedMessage() = _state.update { it.copy(savedMessage = null) }
    fun dismissError() = _state.update { it.copy(error = null) }
}
