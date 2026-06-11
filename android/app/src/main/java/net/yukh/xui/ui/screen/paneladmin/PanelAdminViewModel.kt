package net.yukh.xui.ui.screen.paneladmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.yukh.xui.data.api.dto.ApiToken
import net.yukh.xui.data.repo.PanelRepository

data class PanelAdminUiState(
    val tokensLoading: Boolean = true,
    val tokens: List<ApiToken> = emptyList(),
    val busy: Boolean = false,
    /** A freshly created token — its plaintext value is shown once for copying. */
    val newToken: ApiToken? = null,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Panel administration over the token-accessible `/panel/api/setting/` surface:
 * change the admin credentials, manage API tokens, and restart the panel. All
 * mutate the live panel — the screen confirms the destructive ones first.
 */
@HiltViewModel
class PanelAdminViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PanelAdminUiState())
    val state: StateFlow<PanelAdminUiState> = _state.asStateFlow()

    fun loadTokens() {
        _state.update { it.copy(tokensLoading = true) }
        viewModelScope.launch {
            repo.listApiTokens()
                .onSuccess { rows -> _state.update { it.copy(tokensLoading = false, tokens = rows) } }
                .onFailure { e -> _state.update { it.copy(tokensLoading = false, error = e.message ?: "Couldn't load tokens") } }
        }
    }

    fun changeCredentials(oldU: String, oldP: String, newU: String, newP: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            repo.changeCredentials(oldU.trim(), oldP, newU.trim(), newP)
                .onSuccess { _state.update { it.copy(busy = false, message = "Credentials updated") } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Couldn't change credentials") } }
        }
    }

    fun restartPanel() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            repo.restartPanel()
                .onSuccess { _state.update { it.copy(busy = false, message = "Panel is restarting…") } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Couldn't restart the panel") } }
        }
    }

    fun createToken(name: String) {
        if (_state.value.busy || name.isBlank()) return
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            repo.createApiToken(name.trim())
                .onSuccess { tok ->
                    _state.update { it.copy(busy = false, newToken = tok) }
                    loadTokens()
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Couldn't create token") } }
        }
    }

    fun setTokenEnabled(id: Int, enabled: Boolean) {
        viewModelScope.launch {
            repo.setApiTokenEnabled(id, enabled)
                .onSuccess { loadTokens() }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Couldn't update token") } }
        }
    }

    fun deleteToken(id: Int) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repo.deleteApiToken(id)
                .onSuccess { _state.update { it.copy(busy = false, message = "Token deleted") }; loadTokens() }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Couldn't delete token") } }
        }
    }

    fun dismissNewToken() = _state.update { it.copy(newToken = null) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun dismissError() = _state.update { it.copy(error = null) }
}
