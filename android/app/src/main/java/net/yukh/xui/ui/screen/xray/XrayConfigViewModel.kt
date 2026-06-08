package net.yukh.xui.ui.screen.xray

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
import net.yukh.xui.data.repo.PanelRepository

private val prettyJson = Json { prettyPrint = true; isLenient = true }

data class XrayConfigUiState(
    val loading: Boolean = true,
    val available: Boolean = false,
    val configText: String = "",
    val testUrl: String = "https://www.google.com/generate_204",
    val saving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
)

@HiltViewModel
class XrayConfigViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(XrayConfigUiState())
    val state: StateFlow<XrayConfigUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.getXraySetting()
                .onSuccess { env ->
                    _state.update {
                        it.copy(
                            loading = false,
                            available = true,
                            configText = prettyJson.encodeToString(JsonElement.serializer(), env.xraySetting),
                            testUrl = env.outboundTestUrl,
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            available = false,
                            error = e.message ?: "Xray config unavailable",
                        )
                    }
                }
        }
    }

    fun setConfigText(v: String) = _state.update { it.copy(configText = v, error = null) }
    fun setTestUrl(v: String) = _state.update { it.copy(testUrl = v) }
    fun dismissSavedMessage() = _state.update { it.copy(savedMessage = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        // Validate before sending — a malformed config would break xray.
        val parsed = try {
            prettyJson.parseToJsonElement(s.configText)
        } catch (_: Exception) {
            _state.update { it.copy(error = "Config is not valid JSON") }
            return
        }
        val minified = prettyJson.encodeToString(JsonElement.serializer(), parsed)
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            repo.updateXraySetting(minified, s.testUrl.trim())
                .onSuccess {
                    _state.update { it.copy(saving = false, savedMessage = "Xray config saved — restart Xray to apply") }
                }
                .onFailure { e ->
                    _state.update { it.copy(saving = false, error = e.message ?: "Save failed") }
                }
        }
    }
}
