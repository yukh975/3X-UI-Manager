package net.yukh.xui.ui.screen.clients

import net.yukh.xui.data.api.dto.Client

/**
 * Client status buckets, mirroring the panel's client filter (web `useClients`
 * / `FilterDrawer`). `EXPIRING` is intentionally omitted for now — the panel
 * derives it from configurable expiry/traffic warning thresholds we don't read
 * yet; the four below have exact, self-contained predicates.
 */
enum class ClientStatus { ONLINE, ACTIVE, DISABLED, DEPLETED }

/**
 * Active filters on the client list. Empty sets mean "no constraint". A client
 * is kept when it matches ANY selected status (OR) AND belongs to ANY selected
 * group (OR) — same as the panel, where each section narrows independently.
 */
data class ClientFilters(
    val statuses: Set<ClientStatus> = emptySet(),
    val groups: Set<String> = emptySet(),
) {
    /** Total selected constraints, for the filter-button badge. */
    val count: Int get() = statuses.size + groups.size
    val isEmpty: Boolean get() = statuses.isEmpty() && groups.isEmpty()
}

/**
 * The status buckets a client falls into at [now] (epoch ms). `ONLINE` is
 * reported independently and can overlap; exactly one of ACTIVE/DISABLED/
 * DEPLETED also applies, in the panel's priority order (depleted beats
 * disabled beats active).
 */
fun Client.statusesAt(now: Long, online: Set<String>): Set<ClientStatus> {
    val used = up + down
    val exhausted = quota > 0 && used >= quota
    val expired = expiryTime > 0 && expiryTime <= now
    val result = mutableSetOf<ClientStatus>()
    if (enable && email in online) result += ClientStatus.ONLINE
    when {
        exhausted || expired -> result += ClientStatus.DEPLETED
        !enable -> result += ClientStatus.DISABLED
        else -> result += ClientStatus.ACTIVE
    }
    return result
}

/** True if the client passes [filters] at [now] given the live [online] set. */
fun Client.matches(filters: ClientFilters, now: Long, online: Set<String>): Boolean {
    if (filters.groups.isNotEmpty() && group.trim() !in filters.groups) return false
    if (filters.statuses.isNotEmpty()) {
        val mine = statusesAt(now, online)
        if (filters.statuses.none { it in mine }) return false
    }
    return true
}
