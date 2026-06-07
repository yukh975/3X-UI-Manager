package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Response of GET /panel/api/server/status — refreshed by the panel every 2s.
 * Every field has a default so a panel that adds/removes fields doesn't
 * break parsing (Json is configured with ignoreUnknownKeys + coerceInputValues).
 */
@Serializable
data class ServerStatus(
    val cpu: Double = 0.0,
    val mem: Double = 0.0,
    val memUsed: Long = 0,
    val memTotal: Long = 0,
    val online: Int = 0,
    val xrayRunning: Boolean = false,
    val netUp: Long = 0,
    val netDown: Long = 0,
    val load1: Double = 0.0,
    val load5: Double = 0.0,
    val load15: Double = 0.0,
    val lastUpdate: Long = 0,
)
