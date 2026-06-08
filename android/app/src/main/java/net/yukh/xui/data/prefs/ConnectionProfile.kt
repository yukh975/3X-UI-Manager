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
     * The subscription server's host/port/path aren't reachable with an API
     * token (they live behind the panel's session-only settings), so the user
     * can set this once to enable subscription links + QR in token mode. When
     * blank, the app falls back to reading sub settings (login/password only).
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
 * Two supported auth flavors.
 *  - [Token]: long-lived API token created in the panel under
 *    Settings → Security → API Token. Sent as `Authorization: Bearer …`.
 *  - [Credentials]: username + password (and an ad-hoc 2FA code at login
 *    time). 2FA codes are NEVER persisted; on each app start the user
 *    must re-submit to derive a fresh session.
 */
@Serializable
sealed class ConnectionAuth {

    @Serializable
    @SerialName("token")
    data class Token(val token: String) : ConnectionAuth()

    @Serializable
    @SerialName("credentials")
    data class Credentials(
        val username: String,
        val password: String,
    ) : ConnectionAuth()
}
