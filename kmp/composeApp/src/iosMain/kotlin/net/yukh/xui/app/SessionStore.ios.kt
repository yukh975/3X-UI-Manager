package net.yukh.xui.app

import platform.Foundation.NSUserDefaults

actual class SessionStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun load(): SavedSession? {
        val url = defaults.stringForKey(KEY_URL) ?: return null
        val token = defaults.stringForKey(KEY_TOKEN) ?: return null
        if (url.isBlank() || token.isBlank()) return null
        return SavedSession(url, token)
    }

    actual fun save(baseUrl: String, token: String) {
        defaults.setObject(baseUrl, forKey = KEY_URL)
        defaults.setObject(token, forKey = KEY_TOKEN)
    }

    actual fun clear() {
        defaults.removeObjectForKey(KEY_URL)
        defaults.removeObjectForKey(KEY_TOKEN)
    }

    actual fun loadLang(): String? = defaults.stringForKey(KEY_LANG)

    actual fun saveLang(lang: String) {
        defaults.setObject(lang, forKey = KEY_LANG)
    }

    private companion object {
        const val KEY_URL = "xui.baseUrl"
        const val KEY_TOKEN = "xui.token"
        const val KEY_LANG = "xui.lang"
    }
}
