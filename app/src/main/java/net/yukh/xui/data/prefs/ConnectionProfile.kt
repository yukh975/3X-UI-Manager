package net.yukh.xui.data.prefs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Persisted panel connection. Multi-profile support is out of scope for v1.
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
) {
    companion object {
        fun normalizeUrl(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return trimmed
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
        }
    }
}

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
