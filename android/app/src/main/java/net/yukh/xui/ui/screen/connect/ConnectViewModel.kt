package net.yukh.xui.ui.screen.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.yukh.xui.data.api.dto.ServerStatus
import net.yukh.xui.data.prefs.ConnectionProfile
import net.yukh.xui.data.prefs.ConnectionStore
import net.yukh.xui.data.repo.PanelRepository

data class ConnectUiState(
    val url: String = "",
    val token: String = "",
    val allowInsecureTls: Boolean = false,
    val testing: Boolean = false,
    val error: String? = null,
    val lastResult: ServerStatus? = null,
) {
    val canSubmit: Boolean
        get() = !testing && url.isNotBlank() && token.isNotBlank()
}

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val store: ConnectionStore,
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    private fun initialState(): ConnectUiState {
        val existing = store.getProfile() ?: return ConnectUiState()
        return ConnectUiState(
            url = existing.baseUrl,
            token = existing.token,
            allowInsecureTls = existing.allowInsecureTls,
        )
    }

    fun setUrl(value: String) = _state.update { it.copy(url = value, error = null) }
    fun setToken(value: String) = _state.update { it.copy(token = value, error = null) }
    fun setAllowInsecureTls(value: Boolean) =
        _state.update { it.copy(allowInsecureTls = value, error = null) }

    /**
     * Validate the entered credentials against `/panel/api/server/status`,
     * persist them on success, bind the active API client, and notify the
     * caller so it can navigate forward.
     */
    fun testAndSave(onSuccess: () -> Unit) {
        val s = _state.value
        if (!s.canSubmit) return
        val profile = ConnectionProfile(
            baseUrl = ConnectionProfile.normalizeUrl(s.url),
            token = s.token.trim(),
            allowInsecureTls = s.allowInsecureTls,
        )
        _state.update { it.copy(testing = true, error = null, lastResult = null) }

        viewModelScope.launch {
            repo.testConnection(profile)
                .onSuccess { status ->
                    store.saveProfile(profile)
                    repo.bind(profile)
                    _state.update { it.copy(testing = false, lastResult = status, error = null) }
                    onSuccess()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(testing = false, error = e.message ?: "Connection failed")
                    }
                }
        }
    }
}
