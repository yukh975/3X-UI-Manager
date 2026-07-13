package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Row in GET /panel/api/clients/list on 3x-ui v3.x.
 *
 * Traffic counters live in the nested `traffic` object, NOT at the top
 * level. `tgId` is a numeric Telegram user id (0 when unset) — it is a
 * JSON number, not a string. `inboundIds` lists every inbound the client
 * is attached to.
 */
@Serializable
data class Client(
    val id: Int = 0,
    val email: String = "",
    val uuid: String = "",
    val subId: String = "",
    val flow: String = "",
    val security: String = "",
    val password: String = "",
    val auth: String = "",
    // MTProto (panel 3.5.0): FakeTLS secret + optional advertising tag.
    val secret: String = "",
    val adTag: String = "",
    // WireGuard peer fields — kept so editing a WG client doesn't drop them.
    // NOTE: /clients/list serializes allowedIPs as a comma-separated STRING
    // (the wg_allowed_ips column), unlike the create/update payload which is an
    // array — so it's a String here and split into a list in toModel().
    val privateKey: String = "",
    val publicKey: String = "",
    val preSharedKey: String = "",
    val allowedIPs: String = "",
    val keepAlive: Int = 0,
    val enable: Boolean = true,
    val tgId: Long = 0,
    val limitIp: Int = 0,
    val totalGB: Long = 0,
    val expiryTime: Long = 0,
    val reset: Int = 0,
    val group: String = "",
    val comment: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val inboundIds: List<Int> = emptyList(),
    val traffic: ClientStat? = null,
) {
    val up: Long get() = traffic?.up ?: 0
    val down: Long get() = traffic?.down ?: 0
    /** Quota in bytes; 0 means unlimited. Prefer the traffic counter, fall back to totalGB. */
    val quota: Long get() = traffic?.total?.takeIf { it > 0 } ?: totalGB
    val lastOnline: Long get() = traffic?.lastOnline ?: 0

    /**
     * Rebuild the model.Client shape the panel expects for create/update.
     * The panel's `id` field is the protocol UUID (our `uuid`), NOT the
     * record id. Carries password/auth/security forward so an update of one
     * field doesn't blank the credentials.
     */
    fun toModel(): ClientModel = ClientModel(
        id = uuid,
        email = email,
        password = password,
        auth = auth,
        secret = secret,
        adTag = adTag,
        privateKey = privateKey,
        publicKey = publicKey,
        preSharedKey = preSharedKey,
        allowedIPs = allowedIPs.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        keepAlive = keepAlive,
        security = security,
        flow = flow,
        limitIp = limitIp,
        totalGB = totalGB,
        expiryTime = expiryTime,
        enable = enable,
        tgId = tgId,
        subId = subId,
        group = group,
        comment = comment,
        reset = reset,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
