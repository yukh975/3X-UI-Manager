package net.yukh.xui.shared

import java.util.Locale

actual fun systemLanguage(): String = Locale.getDefault().language.lowercase()
