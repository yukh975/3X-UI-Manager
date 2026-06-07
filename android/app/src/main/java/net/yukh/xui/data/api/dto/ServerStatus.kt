package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Subset of GET /panel/api/server/status payload — enough to render the
 * Dashboard and to confirm a valid auth token.
 *
 * The panel returns more fields (memUsed, memTotal, load1/5/15, lastUpdate,
 * netUp/Down) that we'll surface incrementally. `ignoreUnknownKeys=true` in
 * the Json config means missing fields here don't break parsing.
 */
@Serializable
data class ServerStatus(
    val cpu: Double = 0.0,
    val mem: Double = 0.0,
    val online: Int = 0,
    val xrayRunning: Boolean = false,
    val netUp: Long = 0,
    val netDown: Long = 0,
)
