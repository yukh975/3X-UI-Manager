package net.yukh.xui.app

import platform.Foundation.NSUserDefaults

actual class SessionStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String): String? = defaults.stringForKey(key)
    actual fun putString(key: String, value: String) { defaults.setObject(value, forKey = key) }
    actual fun remove(key: String) { defaults.removeObjectForKey(key) }
}
