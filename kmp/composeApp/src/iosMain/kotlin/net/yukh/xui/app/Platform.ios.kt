package net.yukh.xui.app

import platform.Foundation.NSBundle

actual fun appVersionName(): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "—"
