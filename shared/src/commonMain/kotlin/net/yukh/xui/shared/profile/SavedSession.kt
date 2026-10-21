package net.yukh.xui.shared.profile

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Persisted connection profile (panel base URL + API token + TLS trust), mirroring the
 * Android app's multi-profile model. [name] is an optional display label.
 */
data class SavedSession(
    val baseUrl: String,
    val token: String,
    val allowInsecure: Boolean = false,
    val name: String = "",
    /** Profiles saved before ids existed are keyed by their URL; keep that so their state survives. */
    val id: String = baseUrl,
) {
    /** Label for the panel switcher — the saved name, else the host. */
    val label: String get() = name.ifBlank { hostLabel(baseUrl) }

    fun edited(baseUrl: String, token: String, allowInsecure: Boolean, name: String): SavedSession =
        copy(baseUrl = baseUrl.trim(), token = token.trim(), allowInsecure = allowInsecure, name = name.trim())

    fun connectionDiffersFrom(other: SavedSession): Boolean =
        baseUrl != other.baseUrl || token != other.token || allowInsecure != other.allowInsecure
}

fun List<SavedSession>.urlTakenByOther(id: String, baseUrl: String): Boolean =
    any { it.id != id && it.baseUrl == baseUrl }

@OptIn(ExperimentalUuidApi::class)
fun List<SavedSession>.idFor(baseUrl: String): String =
    firstOrNull { it.baseUrl == baseUrl }?.id ?: Uuid.random().toString()

// Fields joined by U+0001, profiles by U+0002 — control chars that never appear
// in URLs or tokens.

private const val FS = "\u0001"
private const val RS = "\u0002"

fun encodeSessions(list: List<SavedSession>): String =
    list.joinToString(RS) { "${it.name}$FS${it.baseUrl}$FS${it.token}$FS${it.allowInsecure}$FS${it.id}" }

fun decodeSessions(s: String): List<SavedSession> =
    s.split(RS).filter { it.isNotBlank() }.mapNotNull { row ->
        val p = row.split(FS)
        if (p.size >= 4) {
            SavedSession(
                name = p[0], baseUrl = p[1], token = p[2], allowInsecure = p[3] == "true",
                id = p.getOrNull(4)?.takeIf { it.isNotEmpty() } ?: p[1],
            )
        } else null
    }

fun hostLabel(url: String): String = url.substringAfter("://", url).substringBefore("/").ifBlank { url }
