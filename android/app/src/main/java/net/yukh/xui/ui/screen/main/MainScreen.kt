package net.yukh.xui.ui.screen.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import net.yukh.xui.data.repo.PanelRepository
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.navigation.MainTabs
import net.yukh.xui.ui.screen.clients.ClientEditorScreen
import net.yukh.xui.ui.screen.clients.ClientsScreen
import net.yukh.xui.ui.screen.clients.ClientsViewModel
import net.yukh.xui.ui.screen.dashboard.DashboardScreen
import net.yukh.xui.ui.screen.inbounds.InboundEditorScreen
import net.yukh.xui.ui.screen.inbounds.InboundsScreen
import net.yukh.xui.ui.screen.inbounds.InboundsViewModel
import net.yukh.xui.ui.screen.nodes.NodeEditorScreen
import net.yukh.xui.ui.screen.nodes.NodesScreen
import net.yukh.xui.ui.screen.nodes.NodesViewModel
import net.yukh.xui.ui.screen.backup.BackupScreen
import net.yukh.xui.ui.screen.settings.SettingsScreen
import net.yukh.xui.ui.screen.outbounds.OutboundsScreen
import net.yukh.xui.ui.screen.xray.XrayConfigScreen
import net.yukh.xui.ui.screen.xrayedit.DnsScreen
import net.yukh.xui.ui.screen.xrayedit.GeneralScreen
import net.yukh.xui.ui.screen.xrayedit.RoutingScreen

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {
    fun disconnect() = repo.unbind()
}

private data class BottomTabSpec(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    BottomTabSpec(MainTabs.Dashboard, "Dashboard", Icons.Outlined.Dashboard),
    BottomTabSpec(MainTabs.Inbounds, "Inbounds", Icons.Outlined.AllInbox),
    BottomTabSpec(MainTabs.Clients, "Clients", Icons.Outlined.People),
    BottomTabSpec(MainTabs.Nodes, "Nodes", Icons.Outlined.Hub),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onDisconnect: () -> Unit,
    vm: MainViewModel = hiltViewModel(),
) {
    val innerNav = rememberNavController()
    val backEntry by innerNav.currentBackStackEntryAsState()
    val currentRoute = backEntry?.destination?.route ?: MainTabs.Dashboard
    val currentTab = tabs.firstOrNull { it.route == currentRoute } ?: tabs.first()
    var menuOpen by remember { mutableStateOf(false) }
    var showXrayConfig by remember { mutableStateOf(false) }
    var showOutbounds by remember { mutableStateOf(false) }
    var showRouting by remember { mutableStateOf(false) }
    var showDns by remember { mutableStateOf(false) }
    var showGeneral by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // The editor-bearing VMs are created here and shared with the tab screens, so
    // their full-screen editors can be rendered as overlays in THIS (activity)
    // window — Compose Dialog windows don't reliably get system-bar / IME insets,
    // which is why editor action buttons used to hide under the nav bar/keyboard.
    val inboundsVm: InboundsViewModel = hiltViewModel()
    val clientsVm: ClientsViewModel = hiltViewModel()
    val nodesVm: NodesViewModel = hiltViewModel()
    val inboundsState by inboundsVm.state.collectAsStateWithLifecycle()
    val clientsState by clientsVm.state.collectAsStateWithLifecycle()
    val nodesState by nodesVm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr(currentTab.label)) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = tr("Menu"))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(tr("Outbounds")) },
                            leadingIcon = { Icon(Icons.Outlined.SwapVert, contentDescription = null) },
                            onClick = { menuOpen = false; showOutbounds = true },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Routing")) },
                            leadingIcon = { Icon(Icons.Outlined.AltRoute, contentDescription = null) },
                            onClick = { menuOpen = false; showRouting = true },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("DNS")) },
                            leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                            onClick = { menuOpen = false; showDns = true },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("General / Logs")) },
                            leadingIcon = { Icon(Icons.Outlined.Article, contentDescription = null) },
                            onClick = { menuOpen = false; showGeneral = true },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Xray config")) },
                            leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                            onClick = { menuOpen = false; showXrayConfig = true },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Backup / restore")) },
                            leadingIcon = { Icon(Icons.Outlined.Backup, contentDescription = null) },
                            onClick = { menuOpen = false; showBackup = true },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Settings")) },
                            leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            onClick = { menuOpen = false; showSettings = true },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Disconnect")) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                vm.disconnect()
                                onDisconnect()
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            innerNav.navigate(tab.route) {
                                popUpTo(innerNav.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tr(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = innerNav,
            startDestination = MainTabs.Dashboard,
            modifier = Modifier.padding(padding),
        ) {
            composable(MainTabs.Dashboard) { DashboardScreen() }
            composable(MainTabs.Inbounds) { InboundsScreen(vm = inboundsVm) }
            composable(MainTabs.Clients) { ClientsScreen(vm = clientsVm) }
            composable(MainTabs.Nodes) { NodesScreen(vm = nodesVm) }
        }
    }

    // ---- Full-screen overlays (rendered in the activity window so system-bar
    // and keyboard insets work; each is opaque and covers the bottom nav) ----

    inboundsState.editor?.let { editor ->
        BackHandler(onBack = inboundsVm::closeEditor)
        InboundEditorScreen(state = editor, vm = inboundsVm)
    }

    clientsState.editor?.let { editor ->
        BackHandler(onBack = clientsVm::closeEditor)
        ClientEditorScreen(
            state = editor,
            onEmail = clientsVm::setEditorEmail,
            onEnable = clientsVm::setEditorEnable,
            onLimitIp = clientsVm::setEditorLimitIp,
            onTotalGb = clientsVm::setEditorTotalGb,
            onReset = clientsVm::setEditorReset,
            onTgId = clientsVm::setEditorTgId,
            onGroup = clientsVm::setEditorGroup,
            onComment = clientsVm::setEditorComment,
            onExpiry = clientsVm::setEditorExpiry,
            onToggleInbound = clientsVm::toggleEditorInbound,
            onSave = clientsVm::saveEditor,
            onClose = clientsVm::closeEditor,
        )
    }

    nodesState.editor?.let { editor ->
        BackHandler(onBack = nodesVm::closeEditor)
        NodeEditorScreen(
            state = editor,
            onName = nodesVm::setName,
            onRemark = nodesVm::setRemark,
            onScheme = nodesVm::setScheme,
            onAddress = nodesVm::setAddress,
            onPort = nodesVm::setPort,
            onBasePath = nodesVm::setBasePath,
            onApiToken = nodesVm::setApiToken,
            onEnable = nodesVm::setEnable,
            onAllowPrivate = nodesVm::setAllowPrivate,
            onTlsVerifyMode = nodesVm::setTlsVerifyMode,
            onSave = nodesVm::saveEditor,
            onDelete = { nodesVm.deleteNode(editor.id) },
            onClose = nodesVm::closeEditor,
        )
    }

    if (showXrayConfig) {
        BackHandler(onBack = { showXrayConfig = false })
        XrayConfigScreen(onClose = { showXrayConfig = false })
    }

    if (showOutbounds) {
        BackHandler(onBack = { showOutbounds = false })
        OutboundsScreen(onClose = { showOutbounds = false })
    }

    if (showRouting) {
        BackHandler(onBack = { showRouting = false })
        RoutingScreen(onClose = { showRouting = false })
    }

    if (showDns) {
        BackHandler(onBack = { showDns = false })
        DnsScreen(onClose = { showDns = false })
    }

    if (showGeneral) {
        BackHandler(onBack = { showGeneral = false })
        GeneralScreen(onClose = { showGeneral = false })
    }

    if (showBackup) {
        BackHandler(onBack = { showBackup = false })
        BackupScreen(onClose = { showBackup = false })
    }

    if (showSettings) {
        BackHandler(onBack = { showSettings = false })
        SettingsScreen(onClose = { showSettings = false })
    }
    }
}
