package net.yukh.xui.ui.screen.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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
import net.yukh.xui.ui.screen.connect.ConnectScreen
import net.yukh.xui.ui.screen.connect.ConnectViewModel
import net.yukh.xui.ui.screen.profiles.ProfileSwitcherSheet
import net.yukh.xui.ui.screen.clients.ClientEditorScreen
import net.yukh.xui.ui.screen.clients.ClientsScreen
import net.yukh.xui.ui.screen.clients.ClientsViewModel
import net.yukh.xui.ui.screen.dashboard.DashboardScreen
import net.yukh.xui.ui.screen.inbounds.InboundEditorScreen
import net.yukh.xui.ui.screen.inbounds.InboundsScreen
import net.yukh.xui.ui.screen.inbounds.InboundsViewModel
import net.yukh.xui.ui.screen.nodes.NodeEditorScreen
import net.yukh.xui.ui.screen.nodes.NodeMtlsScreen
import net.yukh.xui.ui.screen.nodes.NodesScreen
import net.yukh.xui.ui.screen.nodes.NodesViewModel
import net.yukh.xui.ui.screen.backup.BackupScreen
import net.yukh.xui.ui.screen.paneladmin.PanelAdminScreen
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
    val profiles = repo.profiles
    val activeProfileId = repo.activeProfileId
    fun switchProfile(id: String) { viewModelScope.launch { repo.switchProfile(id) } }
    fun deleteProfile(id: String) = repo.deleteProfile(id)
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
    vm: MainViewModel = hiltViewModel(),
) {
    val innerNav = rememberNavController()
    val backEntry by innerNav.currentBackStackEntryAsState()
    val currentRoute = backEntry?.destination?.route ?: MainTabs.Dashboard
    val currentTab = tabs.firstOrNull { it.route == currentRoute } ?: tabs.first()
    var menuOpen by remember { mutableStateOf(false) }
    // Overlay visibility uses rememberSaveable so it survives activity recreation
    // (e.g. backgrounding the app to copy a panel URL) — you return to the same
    // overlay instead of being dropped back to the Dashboard.
    var showXrayConfig by rememberSaveable { mutableStateOf(false) }
    var showOutbounds by rememberSaveable { mutableStateOf(false) }
    var showRouting by rememberSaveable { mutableStateOf(false) }
    var showDns by rememberSaveable { mutableStateOf(false) }
    var showGeneral by rememberSaveable { mutableStateOf(false) }
    var showBackup by rememberSaveable { mutableStateOf(false) }
    var showPanelAdmin by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showProfiles by rememberSaveable { mutableStateOf(false) }
    var showAddPanel by rememberSaveable { mutableStateOf(false) }

    // Shared Connect VM for the "add panel" overlay; cleared when opening so the
    // form starts blank (vs. the default which pre-fills the active profile).
    val connectVm: ConnectViewModel = hiltViewModel()

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
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val activeProfileId by vm.activeProfileId.collectAsStateWithLifecycle()
    val activeLabel = profiles.firstOrNull { it.id == activeProfileId }?.label

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tr(currentTab.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // Show which panel is active (with multi-profile) so it's
                        // always clear what you're connected to; tap ⇄ to switch.
                        activeLabel?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showProfiles = true }) {
                        Icon(Icons.Outlined.SwapHoriz, contentDescription = tr("Switch panel"))
                    }
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
                            text = { Text(tr("Panel admin")) },
                            leadingIcon = { Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null) },
                            onClick = { menuOpen = false; showPanelAdmin = true },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Node mTLS")) },
                            leadingIcon = { Icon(Icons.Outlined.Security, contentDescription = null) },
                            onClick = { menuOpen = false; nodesVm.openMtls() },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Settings")) },
                            leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            onClick = { menuOpen = false; showSettings = true },
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
            onOutboundTag = nodesVm::setOutboundTag,
            onSave = nodesVm::saveEditor,
            onDelete = { nodesVm.deleteNode(editor.id) },
            onClose = nodesVm::closeEditor,
        )
    }

    if (nodesState.mtlsOpen) {
        BackHandler(onBack = nodesVm::closeMtls)
        NodeMtlsScreen(
            panelCa = nodesState.mtlsCa,
            busy = nodesState.mtlsBusy,
            onSaveTrustCa = nodesVm::saveMtlsTrustCa,
            onClose = nodesVm::closeMtls,
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

    if (showPanelAdmin) {
        BackHandler(onBack = { showPanelAdmin = false })
        PanelAdminScreen(onClose = { showPanelAdmin = false })
    }

    if (showSettings) {
        BackHandler(onBack = { showSettings = false })
        SettingsScreen(onClose = { showSettings = false })
    }

    if (showProfiles) {
        ProfileSwitcherSheet(
            profiles = profiles,
            activeId = activeProfileId,
            onSwitch = vm::switchProfile,
            onAdd = { showProfiles = false; connectVm.clearForm(); showAddPanel = true },
            onDelete = vm::deleteProfile,
            onDismiss = { showProfiles = false },
        )
    }

    if (showAddPanel) {
        BackHandler(onBack = { showAddPanel = false })
        ConnectScreen(
            onConnected = { showAddPanel = false },
            addMode = true,
            onClose = { showAddPanel = false },
            vm = connectVm,
        )
    }
    }
}
