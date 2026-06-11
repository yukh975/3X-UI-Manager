package net.yukh.xui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import net.yukh.xui.data.repo.PanelRepository
import net.yukh.xui.i18n.LanguageState
import net.yukh.xui.i18n.LocalAppLanguage
import net.yukh.xui.security.LockState
import net.yukh.xui.ui.navigation.AppNav
import net.yukh.xui.ui.screen.lock.LockScreen
import net.yukh.xui.ui.theme.XuiTheme

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var languageState: LanguageState

    @Inject
    lateinit var lockState: LockState

    @Inject
    lateinit var repo: PanelRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Re-lock when the app goes to the background — but only while signed in.
        // The passcode guards the panel UI, not the token-entry screen, so a
        // logged-out app (on Connect) never arms the lock and never prompts.
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && repo.connected.value) {
                    lockState.lockIfEnabled()
                }
            },
        )

        setContent {
            val lang by languageState.language.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppLanguage provides lang) {
                XuiTheme {
                    val locked by lockState.locked.collectAsStateWithLifecycle()
                    val connected by repo.connected.collectAsStateWithLifecycle()
                    // The lock only gates the signed-in UI. When not connected the
                    // Connect screen (no panel data) is shown without a passcode.
                    if (locked && connected) {
                        LockScreen(lockState = lockState, onUnlocked = {})
                    } else {
                        AppNav()
                    }
                }
            }
        }
    }
}
