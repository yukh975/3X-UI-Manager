package net.yukh.xui.data.api.dto

/**
 * What an API token is allowed to reach (panel v3.7.0). A panel older than that
 * has no scopes at all and treats every token as [ADMIN], which is why that is
 * the default everywhere.
 */
object ApiTokenScope {
    /** Full access — what every token was before 3.7.0. */
    const val ADMIN = "admin"

    /** Read-only status: server status, metric histories, versions. */
    const val MONITOR = "monitor"

    /** What a central panel needs to sync: inbounds, clients, restart Xray. */
    const val NODE_SYNC = "node-sync"

    val ALL = listOf(ADMIN, MONITOR, NODE_SYNC)
}
