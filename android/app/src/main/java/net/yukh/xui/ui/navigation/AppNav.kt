package net.yukh.xui.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import net.yukh.xui.data.repo.PanelRepository
import net.yukh.xui.ui.screen.connect.ConnectScreen
import net.yukh.xui.ui.screen.main.MainScreen

@HiltViewModel
class AppNavViewModel @Inject constructor(
    repo: PanelRepository,
) : ViewModel() {
    val connected: StateFlow<Boolean> = repo.connected
}

/**
 * Top-level routing is just two states driven by the repository's `connected`
 * flow — no NavHost needed. Connecting flips `connected` → Main; disconnecting
 * flips it back → Connect. A stored token profile is bound (and `connected` set)
 * in the repository's init, so the app opens straight to Main.
 */
@Composable
fun AppNav(vm: AppNavViewModel = hiltViewModel()) {
    val connected by vm.connected.collectAsStateWithLifecycle()
    if (connected) MainScreen(onDisconnect = {}) else ConnectScreen(onConnected = {})
}
