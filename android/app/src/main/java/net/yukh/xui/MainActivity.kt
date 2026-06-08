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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Re-lock when the app goes to the background (if a passcode is set).
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) lockState.lockIfEnabled()
            },
        )

        setContent {
            val lang by languageState.language.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppLanguage provides lang) {
                XuiTheme {
                    val locked by lockState.locked.collectAsStateWithLifecycle()
                    if (locked) {
                        LockScreen(lockState = lockState, onUnlocked = {})
                    } else {
                        AppNav()
                    }
                }
            }
        }
    }
}
