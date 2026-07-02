package net.yukh.xui.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import net.yukh.xui.data.repo.PanelRepository
import net.yukh.xui.ui.screen.connect.ConnectScreen
import net.yukh.xui.ui.screen.main.MainScreen
import net.yukh.xui.ui.screen.settings.SettingsScreen
import net.yukh.xui.update.UpdateDialogHost
import net.yukh.xui.update.UpdateViewModel

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
    // Pre-sign-in settings: reachable from the Connect screen's gear so language /
    // version / update check are available on a fresh install (no ⋮ menu yet).
    var showSettings by rememberSaveable { mutableStateOf(false) }
    when {
        connected -> MainScreen()
        showSettings -> {
            val updateVm: UpdateViewModel = hiltViewModel()
            BackHandler { showSettings = false }
            SettingsScreen(
                onClose = { showSettings = false },
                onCheckUpdates = { updateVm.checkNow() },
                showAppLock = false,
            )
            UpdateDialogHost(updateVm)
        }
        else -> ConnectScreen(onConnected = {}, onSettings = { showSettings = true })
    }
}
