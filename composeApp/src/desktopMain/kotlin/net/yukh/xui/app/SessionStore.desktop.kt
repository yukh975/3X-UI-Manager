package net.yukh.xui.app

import java.util.prefs.Preferences

/** Desktop persistence via java.util.prefs (per-user). */
actual class SessionStore {
    private val p = Preferences.userRoot().node("net/yukh/xui")

    actual fun getString(key: String): String? = p.get(key, null)
    actual fun putString(key: String, value: String) { p.put(key, value) }
    actual fun remove(key: String) { p.remove(key) }
}
