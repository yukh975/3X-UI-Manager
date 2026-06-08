package net.yukh.xui.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** iOS entry point: hand this UIViewController to a SwiftUI/UIKit host. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
