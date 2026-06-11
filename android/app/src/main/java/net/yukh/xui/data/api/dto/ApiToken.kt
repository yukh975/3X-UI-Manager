package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * A panel API token (Settings → Security → API Token). In the **list** the
 * [token] field is the stored SHA-256 hash (not usable); the plaintext is
 * returned **only once**, in the response to create, for the user to copy.
 */
@Serializable
data class ApiToken(
    val id: Int = 0,
    val name: String = "",
    val token: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = 0,
)
