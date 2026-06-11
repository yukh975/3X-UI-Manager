package net.yukh.xui.app

import java.util.prefs.Preferences

/** Desktop persistence via java.util.prefs (per-user). */
actual class SessionStore {
    private val p = Preferences.userRoot().node("net/yukh/xui")

    actual fun load(): SavedSession? {
        val url = p.get(KEY_URL, "")
        val token = p.get(KEY_TOKEN, "")
        if (url.isBlank() || token.isBlank()) return null
        return SavedSession(url, token, p.getBoolean(KEY_INSECURE, false))
    }

    actual fun save(baseUrl: String, token: String, allowInsecure: Boolean) {
        p.put(KEY_URL, baseUrl)
        p.put(KEY_TOKEN, token)
        p.putBoolean(KEY_INSECURE, allowInsecure)
    }

    actual fun clear() {
        p.remove(KEY_URL)
        p.remove(KEY_TOKEN)
        p.remove(KEY_INSECURE)
    }

    actual fun loadLang(): String? = p.get(KEY_LANG, null)
    actual fun saveLang(lang: String) { p.put(KEY_LANG, lang) }

    private companion object {
        const val KEY_URL = "baseUrl"
        const val KEY_TOKEN = "token"
        const val KEY_INSECURE = "allowInsecure"
        const val KEY_LANG = "lang"
    }
}
