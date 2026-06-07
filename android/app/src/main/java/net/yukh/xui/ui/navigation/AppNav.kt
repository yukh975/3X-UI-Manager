package net.yukh.xui.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

@Composable
fun AppNav(vm: AppNavViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val connected by vm.connected.collectAsState()
    val start = if (connected) Routes.Main else Routes.Connect

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.Connect) {
            ConnectScreen(
                onConnected = {
                    navController.navigate(Routes.Main) {
                        popUpTo(Routes.Connect) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Main) {
            MainScreen(
                onDisconnect = {
                    navController.navigate(Routes.Connect) {
                        popUpTo(Routes.Main) { inclusive = true }
                    }
                },
            )
        }
    }
}
