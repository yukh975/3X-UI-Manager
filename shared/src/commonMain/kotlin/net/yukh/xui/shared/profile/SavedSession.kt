package net.yukh.xui.shared.profile

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

// Fields joined by U+0001, profiles by U+0002 — control chars that never appear
// in URLs or tokens.

private const val FS = "\u0001"
private const val RS = "\u0002"

fun encodeSessions(list: List<SavedSession>): String =
    list.joinToString(RS) { "${it.name}$FS${it.baseUrl}$FS${it.token}$FS${it.allowInsecure}" }

fun decodeSessions(s: String): List<SavedSession> =
    s.split(RS).filter { it.isNotBlank() }.mapNotNull { row ->
        val p = row.split(FS)
        if (p.size >= 4) SavedSession(name = p[0], baseUrl = p[1], token = p[2], allowInsecure = p[3] == "true")
        else null
    }

private fun hostOf(url: String): String = url.substringAfter("://", url).substringBefore("/").ifBlank { url }
