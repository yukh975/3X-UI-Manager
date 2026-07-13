package net.yukh.xui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    lateinit var speedUnitState: net.yukh.xui.data.prefs.SpeedUnitState

    @Inject
    lateinit var lockState: LockState

    @Inject
    lateinit var repo: PanelRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Re-lock on returning from the background — but only while signed in, and
        // only after a grace period. A quick switch (e.g. to another app to copy a
        // panel URL) and back within the window does NOT prompt for the passcode,
        // so you stay exactly where you were. The grace timer lives in LockState
        // (a singleton) so it survives activity recreation; the launch lock for a
        // returning user is handled separately by LockState's initial value.
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP ->
                        if (repo.connected.value) lockState.onBackgrounded()
                    Lifecycle.Event.ON_START ->
                        if (repo.connected.value) lockState.lockIfBackgroundedFor(LOCK_GRACE_MS)
                    else -> {}
                }
            },
        )

        setContent {
            val lang by languageState.language.collectAsStateWithLifecycle()
            val speedInBits by speedUnitState.inBits.collectAsStateWithLifecycle()
            CompositionLocalProvider(
                LocalAppLanguage provides lang,
                net.yukh.xui.ui.format.LocalSpeedInBits provides speedInBits,
            ) {
                XuiTheme {
                    val locked by lockState.locked.collectAsStateWithLifecycle()
                    val connected by repo.connected.collectAsStateWithLifecycle()
                    // Keep the app composed and overlay the (opaque) lock on top, so
                    // unlocking returns to exactly where you were instead of rebuilding
                    // the UI back to the Dashboard. The lock only gates the signed-in
                    // UI — when not connected the Connect screen shows without it.
                    Box(Modifier.fillMaxSize()) {
                        AppNav()
                        if (locked && connected) {
                            LockScreen(lockState = lockState, onUnlocked = {})
                        }
                    }
                }
            }
        }
    }

    private companion object {
        /** Grace before a backgrounded, signed-in app re-locks. */
        const val LOCK_GRACE_MS = 30_000L
    }
}
