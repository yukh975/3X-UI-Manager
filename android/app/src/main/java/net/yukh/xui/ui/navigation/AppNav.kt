package net.yukh.xui.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import net.yukh.xui.ui.screen.dashboard.DashboardScreen

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
    val start = if (connected) Routes.Dashboard else Routes.Connect

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.Connect) {
            ConnectScreen(
                onConnected = {
                    navController.navigate(Routes.Dashboard) {
                        popUpTo(Routes.Connect) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Dashboard) {
            DashboardScreen(
                onDisconnect = {
                    navController.navigate(Routes.Connect) {
                        popUpTo(Routes.Dashboard) { inclusive = true }
                    }
                },
            )
        }
    }
}
