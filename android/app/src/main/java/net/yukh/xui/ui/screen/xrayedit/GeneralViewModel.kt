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
import net.yukh.xui.data.repo.PanelRepository

data class GeneralUiState(
    val loading: Boolean = true,
    val available: Boolean = false,
    val config: JsonObject = JsonObject(emptyMap()),
    val testUrl: String = "https://www.google.com/generate_204",
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
)

@HiltViewModel
class GeneralViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GeneralUiState())
    val state: StateFlow<GeneralUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.loadXrayConfig()
                .onSuccess { (cfg, url) ->
                    _state.update {
                        it.copy(loading = false, available = true, config = cfg, testUrl = url, dirty = false, error = null)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, available = false, error = e.message ?: "Xray config unavailable") }
                }
        }
    }

    fun update(config: JsonObject) = _state.update { it.copy(config = config, dirty = true) }
    fun setTestUrl(v: String) = _state.update { it.copy(testUrl = v, dirty = true) }

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

    fun dismissSavedMessage() = _state.update { it.copy(savedMessage = null) }
    fun dismissError() = _state.update { it.copy(error = null) }
}
