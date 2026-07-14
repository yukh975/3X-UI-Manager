package net.yukh.xui.app

/**
 * Persisted connection profile (panel base URL + API token + TLS trust). Keyed by
 * [baseUrl] — one profile per panel URL, mirroring the Android app's multi-profile
 * (multi-instance) model. [name] is an optional display label.
 */
data class SavedSession(
    val baseUrl: String,
    val token: String,
    val allowInsecure: Boolean = false,
    val name: String = "",
) {
    /** Stable identifier — the panel URL. */
    val id: String get() = baseUrl

    /** Label for the panel switcher — the saved name, else the host. */
    val label: String get() = name.ifBlank { hostOf(baseUrl) }
}

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

/** Add the profile (or replace the one with the same URL), keeping order, and make it active. */
fun SessionStore.upsertActive(session: SavedSession) {
    val all = loadProfiles()
    val updated = if (all.any { it.id == session.id }) all.map { if (it.id == session.id) session else it }
    else all + session
    saveProfiles(updated)
    setActiveId(session.id)
}

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
fun SessionStore.loadSpeedInBits(): Boolean = getString(KEY_SPEED_BITS) == "true"
fun SessionStore.saveSpeedInBits(value: Boolean) = putString(KEY_SPEED_BITS, value.toString())

// ---- encoding (composeApp has no kotlinx-serialization dependency) ----------
// Fields joined by U+0001, profiles by U+0002 — control chars that never appear
// in URLs or tokens.

private const val FS = ""
private const val RS = ""

internal fun encodeSessions(list: List<SavedSession>): String =
    list.joinToString(RS) { "${it.name}$FS${it.baseUrl}$FS${it.token}$FS${it.allowInsecure}" }

internal fun decodeSessions(s: String): List<SavedSession> =
    s.split(RS).filter { it.isNotBlank() }.mapNotNull { row ->
        val p = row.split(FS)
        if (p.size >= 4) SavedSession(name = p[0], baseUrl = p[1], token = p[2], allowInsecure = p[3] == "true")
        else null
    }

internal fun hostOf(url: String): String = url.substringAfter("://", url).substringBefore("/").ifBlank { url }
