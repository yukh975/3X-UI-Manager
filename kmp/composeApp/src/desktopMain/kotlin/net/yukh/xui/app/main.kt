package net.yukh.xui.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** Desktop (JVM) entry point — hosts the shared Compose UI in a window, sized
 *  like a phone since the layout is portrait-first. */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "3X-UI Manager",
        state = rememberWindowState(width = 430.dp, height = 880.dp),
    ) {
        App()
    }
}
