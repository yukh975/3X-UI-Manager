package net.yukh.xui.ui.components

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Make the hosting Dialog window resize when the soft keyboard appears
 * (SOFT_INPUT_ADJUST_RESIZE). Compose `Dialog` windows do not reliably dispatch
 * IME / system-bar WindowInsets, so `imePadding()` / `navigationBarsPadding()`
 * inside a dialog frequently measure 0 — which is why a tall editor form had its
 * Delete button hidden under the navigation bar and its last field hidden by the
 * keyboard.
 *
 * With the dialog left in its default decor-fitting mode (system bars honored,
 * so content already clears the nav bar) plus ADJUST_RESIZE, the window shrinks
 * when the keyboard shows and the scrollable content can bring the focused field
 * into view. Call this from inside a Dialog content lambda.
 */
@Composable
fun AdjustResizeDialogWindow() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window
            ?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
