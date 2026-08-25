package net.yukh.xui.shared.dto

import kotlinx.serialization.Serializable

/**
 * A panel API token. In the **list** [token] is the stored SHA-256 hash (not
 * usable); the plaintext is returned **only once**, in the create response, for
 * the user to copy.
 */
@Serializable
data class ApiToken(
    val id: Int = 0,
    val name: String = "",
    val token: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = 0,
    // Panel v3.7.0: what the token may reach, and when it stops working. An
    // older panel omits both; "admin" + 0 is exactly how it behaves.
    val scope: String = "admin",
    val expiresAt: Long = 0,
)
