package net.yukh.xui.app

/** Persisted connection (panel base URL + API token). */
data class SavedSession(val baseUrl: String, val token: String)

/**
 * Cross-platform persistence for the connection so the user doesn't re-enter it
 * every launch. iOS = NSUserDefaults, JVM = java.util.prefs.
 *
 * TODO(security): the token is sensitive — move iOS storage to the Keychain
 * (and Android to EncryptedSharedPreferences, matching the current Android app).
 */
expect class SessionStore() {
    fun load(): SavedSession?
    fun save(baseUrl: String, token: String)
    fun clear()
}
