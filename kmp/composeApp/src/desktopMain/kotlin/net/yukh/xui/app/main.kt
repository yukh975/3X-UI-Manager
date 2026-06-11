package net.yukh.xui.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** Desktop (JVM) entry point — hosts the shared Compose UI in a window. The
 *  layout is portrait-first, but on desktop we open it ~3× wider than a phone so
 *  there's room to work; the content simply gets more horizontal breathing room. */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "3X-UI Manager",
        state = rememberWindowState(width = 1290.dp, height = 880.dp),
    ) {
        App()
    }
}
