package net.yukh.xui.data.prefs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Persisted panel connection. The app stores several of these (multi-profile)
 * and switches the active one at runtime; [id] identifies a profile and [name]
 * is its display label in the switcher (defaults to the host).
 *
 * `baseUrl` is normalized to end with `/` so Retrofit treats it as a
 * directory and appends paths cleanly. Include the panel's `webBasePath`
 * if the admin configured one (e.g. `https://panel:2053/x-ui/`).
 */
@Serializable
data class ConnectionProfile(
    val baseUrl: String,
    val allowInsecureTls: Boolean = false,
    val auth: ConnectionAuth,
    /**
     * Optional subscription base URL, e.g. `https://panel.example.com:2096/sub/`.
     * On v3.3.0 the app reads the base from panel settings automatically (token
     * or login), so this is only needed as an override — for panels older than
     * v3.3.0 (settings not token-readable) or a reverse proxy whose public URL
     * differs from what the panel stores. When blank, the base is auto-read.
     */
    val subBaseUrl: String = "",
    // Stable identifier for the profile (assigned when saved). Defaulted so older
    // single-profile blobs deserialize cleanly and get an id on migration.
    val id: String = "",
    // Optional display name; falls back to the host in [label].
    val name: String = "",
) {
    /** Label shown in the profile switcher — the saved name, else the host. */
    val label: String get() = name.ifBlank { hostLabel(baseUrl) }

    // Before 0.15.5 every connect stored the host itself as the name; that is not
    // an alias and must not survive a URL edit as a stale label.
    val customName: String get() = name.takeUnless { it == hostLabel(baseUrl) } ?: ""

    fun edited(
        baseUrl: String,
        allowInsecureTls: Boolean,
        token: String,
        subBaseUrl: String,
        name: String,
    ): ConnectionProfile = copy(
        baseUrl = baseUrl,
        allowInsecureTls = allowInsecureTls,
        auth = ConnectionAuth.Token(token),
        subBaseUrl = subBaseUrl,
        name = storedName(name, baseUrl),
    )

    fun connectionDiffersFrom(other: ConnectionProfile): Boolean =
        baseUrl != other.baseUrl || allowInsecureTls != other.allowInsecureTls || auth != other.auth

    companion object {
        fun normalizeUrl(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return trimmed
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
        }

        fun storedName(input: String, baseUrl: String): String =
            input.trim().takeUnless { it == hostLabel(baseUrl) } ?: ""

        /** host[:port] from a base URL, for a default profile label. */
        fun hostLabel(url: String): String =
            url.substringAfter("://", url).substringBefore("/").ifBlank { url }
    }
}

fun List<ConnectionProfile>.urlTakenByOther(id: String, baseUrl: String): Boolean =
    any { it.id != id && it.baseUrl == baseUrl }

/**
 * Auth is a long-lived API token (created in the panel under Settings → Security
 * → API Token), sent as `Authorization: Bearer …`. The app is token-only and
 * targets panel **v3.3.0+**, where a token reaches the whole management API.
 */
@Serializable
sealed class ConnectionAuth {

    @Serializable
    @SerialName("token")
    data class Token(val token: String) : ConnectionAuth()
}
