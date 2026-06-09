package net.yukh.xui.app

/** Persisted connection (panel base URL + API token + TLS trust setting). */
data class SavedSession(val baseUrl: String, val token: String, val allowInsecure: Boolean = false)

/**
 * Cross-platform persistence for the connection so the user doesn't re-enter it
 * every launch. iOS = NSUserDefaults, JVM = java.util.prefs.
 *
 * TODO(security): the token is sensitive — move iOS storage to the Keychain
 * (and Android to EncryptedSharedPreferences, matching the current Android app).
 */
expect class SessionStore() {
    fun load(): SavedSession?
    fun save(baseUrl: String, token: String, allowInsecure: Boolean)
    fun clear()

    /** Persisted UI language ("en"/"ru"); null = not set (default English). */
    fun loadLang(): String?
    fun saveLang(lang: String)
}
