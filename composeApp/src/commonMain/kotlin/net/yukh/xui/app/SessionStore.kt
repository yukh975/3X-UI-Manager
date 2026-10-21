package net.yukh.xui.app

import net.yukh.xui.shared.profile.SavedSession
import net.yukh.xui.shared.profile.decodeSessions
import net.yukh.xui.shared.profile.encodeSessions

/**
 * Cross-platform key/value persistence (iOS = NSUserDefaults, JVM = java.util.prefs).
 * The multi-profile + language helpers below build on this so the logic stays shared.
 *
 * TODO(security): the token is sensitive — move iOS storage to the Keychain.
 */
expect class SessionStore() {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

private const val KEY_PROFILES = "xui.profiles"
private const val KEY_ACTIVE = "xui.activeId"
private const val KEY_LANG = "xui.lang"
private const val KEY_SPEED_BITS = "xui.speedInBits"
private const val KEY_UPDATE_DISMISSED = "xui.update.dismissed"
private const val KEY_UPDATE_CHANNEL = "xui.update.channel"

/** All saved profiles; migrates a pre-multi-profile single session once. */
fun SessionStore.loadProfiles(): List<SavedSession> {
    getString(KEY_PROFILES)?.let { return decodeSessions(it) }
    // Legacy single-session keys differed by platform (iOS "xui.baseUrl", desktop "baseUrl").
    val url = getString("xui.baseUrl") ?: getString("baseUrl")
    val token = getString("xui.token") ?: getString("token")
    if (url.isNullOrBlank() || token.isNullOrBlank()) return emptyList()
    val migrated = listOf(SavedSession(url, token))
    saveProfiles(migrated)
    setActiveId(migrated.first().id)
    remove("xui.baseUrl"); remove("xui.token"); remove("xui.allowInsecure")
    remove("baseUrl"); remove("token"); remove("allowInsecure")
    return migrated
}

fun SessionStore.saveProfiles(profiles: List<SavedSession>) = putString(KEY_PROFILES, encodeSessions(profiles))

fun SessionStore.activeId(): String? = getString(KEY_ACTIVE)
fun SessionStore.setActiveId(id: String) = putString(KEY_ACTIVE, id)

/** The active profile (or the first saved, or null). */
fun SessionStore.activeProfile(): SavedSession? {
    val all = loadProfiles()
    return all.firstOrNull { it.id == activeId() } ?: all.firstOrNull()
}

/** Add the profile (or replace the one with the same id), keeping order, and make it active. */
fun SessionStore.upsertActive(session: SavedSession) {
    val all = loadProfiles()
    val updated = if (all.any { it.id == session.id }) all.map { if (it.id == session.id) session else it }
    else all + session
    saveProfiles(updated)
    setActiveId(session.id)
}

fun SessionStore.replaceProfile(session: SavedSession) =
    saveProfiles(loadProfiles().map { if (it.id == session.id) session else it })

/** Remove a profile; returns the remaining list. */
fun SessionStore.removeProfile(id: String): List<SavedSession> {
    val left = loadProfiles().filterNot { it.id == id }
    saveProfiles(left)
    return left
}

fun SessionStore.clearAll() { remove(KEY_PROFILES); remove(KEY_ACTIVE) }

fun SessionStore.loadLang(): String? = getString(KEY_LANG)
fun SessionStore.saveLang(lang: String) = putString(KEY_LANG, lang)

/** Live-speed unit choice: true = bits/s, false (default) = bytes/s. */
/** The release the user already waved away. The launch check stays quiet about
 *  it — on iOS an update cannot be installed from inside the app, so without
 *  this the same prompt reappears on every single launch until the user
 *  sideloads the new build. A manual check ignores it. */
fun SessionStore.dismissedUpdate(): String? = getString(KEY_UPDATE_DISMISSED)

fun SessionStore.setDismissedUpdate(version: String) = putString(KEY_UPDATE_DISMISSED, version)

fun SessionStore.loadSpeedInBits(): Boolean = getString(KEY_SPEED_BITS) == "true"
fun SessionStore.saveSpeedInBits(value: Boolean) = putString(KEY_SPEED_BITS, value.toString())

/** Update channel: "stable" (GitHub) or "testing" (home GitLab). Default stable. */
fun SessionStore.loadUpdateChannel(): String = getString(KEY_UPDATE_CHANNEL) ?: "stable"
fun SessionStore.saveUpdateChannel(channel: String) = putString(KEY_UPDATE_CHANNEL, channel)
