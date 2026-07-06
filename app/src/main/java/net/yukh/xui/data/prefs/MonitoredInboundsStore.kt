package net.yukh.xui.data.prefs

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * An inbound the user opted to monitor for reachability. The port + remark are
 * snapshotted when the checkbox is ticked, so the background alert probe can hit
 * the port directly without re-fetching the panel API (which may be firewalled
 * off the phone). Keyed per connection profile — inbound ids aren't unique across
 * panels.
 */
@Serializable
data class MonitoredInbound(val id: Int, val port: Int, val remark: String = "")

/** Local per-profile set of inbounds flagged for reachability monitoring. */
@Singleton
class MonitoredInboundsStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun list(profileId: String): List<MonitoredInbound> =
        prefs.getString(profileId, null)
            ?.let { runCatching { json.decodeFromString<List<MonitoredInbound>>(it) }.getOrNull() }
            ?: emptyList()

    fun isMonitored(profileId: String, inboundId: Int): Boolean =
        list(profileId).any { it.id == inboundId }

    /** Add or update (on=true) / remove (on=false) an inbound for [profileId]. */
    fun setMonitored(profileId: String, inbound: MonitoredInbound, on: Boolean) {
        val rest = list(profileId).filterNot { it.id == inbound.id }
        val next = if (on) rest + inbound else rest
        if (next.isEmpty()) prefs.edit { remove(profileId) }
        else prefs.edit { putString(profileId, json.encodeToString(next)) }
    }

    private companion object {
        const val FILE = "xui_monitored_inbounds"
    }
}
