package net.yukh.xui.app

import platform.Foundation.NSUserDefaults

actual class SessionStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String): String? = defaults.stringForKey(key)

    // Flush after every mutation: profile/credential changes are infrequent but
    // important, and we don't want one lost if the app is killed before iOS's
    // periodic flush. synchronize() is deprecated yet still the explicit flush.
    actual fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
        defaults.synchronize()
    }

    actual fun remove(key: String) {
        defaults.removeObjectForKey(key)
        defaults.synchronize()
    }
}
