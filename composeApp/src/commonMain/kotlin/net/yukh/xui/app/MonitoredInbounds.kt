package net.yukh.xui.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * An inbound the user opted to monitor for reachability. Port + remark are
 * snapshotted when the checkbox is ticked so the background alert probe can hit
 * the port directly, without re-fetching the panel API (often firewalled off the
 * phone). Stored per connection profile — inbound ids aren't unique across panels.
 */
@Serializable
data class MonitoredInbound(val id: Int, val port: Int, val remark: String = "")

private val monitoredJson = Json { ignoreUnknownKeys = true }

private fun key(profileId: String) = "xui.monitored.$profileId"

fun SessionStore.monitoredInbounds(profileId: String): List<MonitoredInbound> =
    getString(key(profileId))
        ?.let { runCatching { monitoredJson.decodeFromString<List<MonitoredInbound>>(it) }.getOrNull() }
        ?: emptyList()

fun SessionStore.isInboundMonitored(profileId: String, inboundId: Int): Boolean =
    monitoredInbounds(profileId).any { it.id == inboundId }

/** Add/update (on=true) or remove (on=false) an inbound for [profileId]. */
fun SessionStore.setInboundMonitored(profileId: String, inbound: MonitoredInbound, on: Boolean) {
    val rest = monitoredInbounds(profileId).filterNot { it.id == inbound.id }
    val next = if (on) rest + inbound else rest
    if (next.isEmpty()) remove(key(profileId))
    else putString(key(profileId), monitoredJson.encodeToString(next))
}
