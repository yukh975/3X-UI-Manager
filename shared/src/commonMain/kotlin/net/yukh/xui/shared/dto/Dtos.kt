package net.yukh.xui.shared.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// ---- Response envelopes (web/entity/Msg.go) ------------------------------

@Serializable
data class ApiResponse<T>(
    val success: Boolean = false,
    val msg: String = "",
    val obj: T? = null,
)

@Serializable
data class ApiAck(
    val success: Boolean = false,
    val msg: String = "",
)

// ---- Server status (GET /panel/api/server/status) ------------------------

@Serializable
data class ServerStatus(
    val cpu: Double = 0.0,
    val cpuCores: Int = 0,
    val logicalPro: Int = 0,
    val cpuSpeedMhz: Double = 0.0,
    val mem: ResourceUsage = ResourceUsage(),
    val swap: ResourceUsage = ResourceUsage(),
    val disk: ResourceUsage = ResourceUsage(),
    val xray: XrayStatus = XrayStatus(),
    val panelVersion: String = "",
    val uptime: Long = 0,
    val loads: List<Double> = emptyList(),
    val tcpCount: Int = 0,
    val udpCount: Int = 0,
    val netIO: NetIO = NetIO(),
    val netTraffic: NetTraffic = NetTraffic(),
    val publicIP: PublicIP = PublicIP(),
    val appStats: AppStats = AppStats(),
) {
    val xrayRunning: Boolean get() = xray.state.equals("running", ignoreCase = true)
    val load1: Double get() = loads.getOrElse(0) { 0.0 }
    val load5: Double get() = loads.getOrElse(1) { 0.0 }
    val load15: Double get() = loads.getOrElse(2) { 0.0 }
    val memPercent: Double get() = if (mem.total > 0) mem.current.toDouble() / mem.total * 100.0 else 0.0
    val diskPercent: Double get() = if (disk.total > 0) disk.current.toDouble() / disk.total * 100.0 else 0.0

    /** Swap used percentage, 0..100. Servers without swap report a zero total. */
    val swapPercent: Double get() = if (swap.total > 0) swap.current.toDouble() / swap.total * 100.0 else 0.0
}

@Serializable
data class ResourceUsage(val current: Long = 0, val total: Long = 0)

@Serializable
data class XrayStatus(val state: String = "", val errorMsg: String = "", val version: String = "")

@Serializable
data class NetIO(val up: Long = 0, val down: Long = 0, val pktUp: Long = 0, val pktDown: Long = 0)

@Serializable
data class NetTraffic(val sent: Long = 0, val recv: Long = 0)

@Serializable
data class PublicIP(val ipv4: String = "", val ipv6: String = "")

@Serializable
data class AppStats(val threads: Int = 0, val mem: Long = 0, val uptime: Long = 0)

// ---- Inbounds (GET /panel/api/inbounds/list) -----------------------------

@Serializable
data class InboundSlim(
    val id: Int = 0,
    val remark: String = "",
    val enable: Boolean = false,
    val listen: String = "",
    val port: Int = 0,
    val protocol: String = "",
    val up: Long = 0,
    val down: Long = 0,
    val total: Long = 0,
    val expiryTime: Long = 0,
    val tag: String = "",
    val trafficReset: String = "",
    // When the inbound's counter was last zeroed (Unix ms; 0 = never). For a
    // monthly inbound this is the start of the current accounting month.
    val lastTrafficResetTime: Long = 0,
    // null/0 = the main panel's own inbound; N = sub-node id (central list mixes both).
    val nodeId: Int? = null,
    val clientStats: List<ClientStat> = emptyList(),
)

/** Proxied (VPN) traffic for one server (Σ up+down over its inbounds). */
data class TrafficSummary(val bytes: Long, val sinceMillis: Long, val allMonthly: Boolean)

/**
 * Traffic this month grouped by server: key 0 = main panel's own inbounds
 * (nodeId null/0), key N = sub-node N. Mirrors Android's monthlyTrafficByServer.
 */
fun trafficByNode(inbounds: List<InboundSlim>): Map<Int, TrafficSummary> =
    inbounds.groupBy { it.nodeId ?: 0 }.mapValues { (_, g) ->
        TrafficSummary(
            bytes = g.sumOf { it.up + it.down },
            sinceMillis = g.mapNotNull { it.lastTrafficResetTime.takeIf { t -> t > 0 } }.maxOrNull() ?: 0L,
            allMonthly = g.all { it.trafficReset.equals("monthly", ignoreCase = true) },
        )
    }

@Serializable
data class ClientStat(
    val id: Int = 0,
    val inboundId: Int = 0,
    val email: String = "",
    val enable: Boolean = true,
    val up: Long = 0,
    val down: Long = 0,
    val total: Long = 0,
    val expiryTime: Long = 0,
    val reset: Int = 0,
    val lastOnline: Long = 0,
)

/**
 * Body for inbound create/update (POST /inbounds/add, /inbounds/update/:id) and
 * the parse target for GET /inbounds/get/:id. settings/streamSettings/sniffing
 * are kept as raw JsonElement so a basic-field edit round-trips the protocol
 * config untouched (the panel emits them as objects; unknown response-only
 * fields like up/down/clientStats are dropped by ignoreUnknownKeys).
 */
@Serializable
data class InboundModel(
    val id: Int = 0,
    val remark: String = "",
    val enable: Boolean = true,
    val listen: String = "",
    val port: Int = 0,
    val protocol: String = "vless",
    val expiryTime: Long = 0,
    val total: Long = 0,
    val trafficReset: String = "never",
    // No editor field, but the panel's update overwrites these from the body, so an
    // edit must send the loaded values back (an empty tag makes the panel generate one).
    val trafficResetDay: Int = 1,
    val tag: String = "",
    val subSortIndex: Int = 1,
    val excludeFromSub: Boolean = false,
    // Panel v3.7.0 (VLESS): opt this inbound out of automatic xtls-rprx-vision.
    val disableFlow: Boolean = false,
    val settings: JsonElement = JsonObject(emptyMap()),
    val streamSettings: JsonElement = JsonObject(emptyMap()),
    val sniffing: JsonElement = JsonObject(emptyMap()),
    val nodeId: Int? = null,
)

@Serializable
data class EnableRequest(val enable: Boolean)

@Serializable
data class InboundIdsRequest(val inboundIds: List<Int>)

// ---- Clients (GET /panel/api/clients/list) -------------------------------

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
    val secret: String = "",
    val adTag: String = "",
    val privateKey: String = "",
    val publicKey: String = "",
    val preSharedKey: String = "",
    // /clients/list serializes allowedIPs as a comma-separated STRING (the
    // wg_allowed_ips column), unlike the create/update body which is an array.
    // Declaring it List<String> here breaks parsing of the whole client list.
    val allowedIPs: String = "",
    @Serializable(with = KeepAliveSerializer::class)
    val keepAlive: String = "",
    val enable: Boolean = true,
    val tgId: Long = 0,
    val limitIp: Int = 0,
    val totalGB: Long = 0,
    val expiryTime: Long = 0,
    val reset: Int = 0,
    // Panel v3.7.0: calendar-day renewal + a cap on auto-renewals, the client's
    // own traffic-reset cycle, the subscription device limit, and AmneziaWG
    // forwarded ports.
    val resetDay: Int = 0,
    val resetMax: Int = 0,
    // Panel v3.9.0 weekly renewal (1-7 = Mon-Sun, 0 = off) and the VLESS simple
    // reverse proxy ({"tag":…} or null). The panel's update overwrites both from the body.
    val resetWeekday: Int = 0,
    val reverse: JsonElement? = null,
    val trafficReset: String = "never",
    val trafficResetDay: Int = 1,
    val limitHwid: Int = 0,
    val forwardedPorts: String = "",
    val group: String = "",
    val comment: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val inboundIds: List<Int> = emptyList(),
    val traffic: ClientStat? = null,
) {
    val up: Long get() = traffic?.up ?: 0
    val down: Long get() = traffic?.down ?: 0
    val quota: Long get() = traffic?.total?.takeIf { it > 0 } ?: totalGB
    val lastOnline: Long get() = traffic?.lastOnline ?: 0

    fun toModel(): ClientModel = ClientModel(
        id = uuid, email = email, password = password, auth = auth, security = security,
        secret = secret, adTag = adTag,
        privateKey = privateKey, publicKey = publicKey, preSharedKey = preSharedKey,
        allowedIPs = allowedIPs.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        // Only a real keepalive goes back: a row's 0 can't tell "off" from "never set",
        // and an explicit 0 would be written into non-tunnel clients too.
        keepAlive = keepAlive.takeIf { it.isNotEmpty() && it != "0" },
        flow = flow, limitIp = limitIp, totalGB = totalGB, expiryTime = expiryTime,
        enable = enable, tgId = tgId, subId = subId, group = group, comment = comment,
        reset = reset, resetDay = resetDay, resetMax = resetMax,
        resetWeekday = resetWeekday, reverse = reverseObject(reverse),
        trafficReset = trafficReset, trafficResetDay = trafficResetDay,
        limitHwid = limitHwid, forwardedPorts = forwardedPorts,
        createdAt = createdAt, updatedAt = updatedAt,
    )
}

@Serializable
data class ClientModel(
    val id: String = "",
    val email: String = "",
    val password: String = "",
    val auth: String = "",
    val secret: String = "",
    val adTag: String = "",
    val privateKey: String = "",
    val publicKey: String = "",
    val preSharedKey: String = "",
    val allowedIPs: List<String> = emptyList(),
    // Null drops the key, so panel v3.8.0 keeps the stored keepalive; any number, 0
    // included, is written into the client's settings whatever its protocol.
    @Serializable(with = KeepAliveSerializer::class)
    val keepAlive: String? = null,
    val security: String = "auto",
    val flow: String = "",
    val limitIp: Int = 0,
    val totalGB: Long = 0,
    val expiryTime: Long = 0,
    val enable: Boolean = true,
    val tgId: Long = 0,
    val subId: String = "",
    val group: String = "",
    val comment: String = "",
    val reset: Int = 0,
    val resetDay: Int = 0,
    val resetMax: Int = 0,
    val resetWeekday: Int = 0,
    // Null leaves it out (explicitNulls = false); the panel then clears the reverse tag.
    val reverse: JsonObject? = null,
    val trafficReset: String = "never",
    val trafficResetDay: Int = 1,
    val limitHwid: Int = 0,
    val forwardedPorts: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

/** The stored reverse as the object the update body takes; an empty or tag-less one
 *  is dropped (the panel clears it either way). Older panels sent it as JSON text. */
private fun reverseObject(raw: JsonElement?): JsonObject? {
    val obj = when (raw) {
        is JsonObject -> raw
        is JsonPrimitive -> raw.contentOrNull?.takeIf { raw.isString && it.isNotBlank() }
            ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        else -> null
    } ?: return null
    val tag = (obj["tag"] as? JsonPrimitive)?.contentOrNull
    return obj.takeIf { !tag.isNullOrBlank() }
}

@Serializable
data class ClientCreatePayload(val client: ClientModel, val inboundIds: List<Int>)

// ---- Outbound connectivity test (POST /panel/api/xray/testOutbound) -------

@Serializable
data class TestOutboundResult(
    val tag: String = "",
    val success: Boolean = false,
    val delay: Long = 0,
    val error: String = "",
    val mode: String = "",
    val httpStatus: Int = 0,
    val connectMs: Long = 0,
    val tlsMs: Long = 0,
    val ttfbMs: Long = 0,
    val endpoints: List<TestEndpointResult> = emptyList(),
    val egress: TestEgressResult? = null,
)

@Serializable
data class TestEndpointResult(
    val address: String = "",
    val success: Boolean = false,
    val delay: Long = 0,
    val error: String = "",
)

@Serializable
data class TestEgressResult(
    val ipv4: String = "",
    val ipv6: String = "",
    val country: String = "",
    val warp: String = "",
)

// ---- Route test (POST /panel/api/xray/routeTest) -------------------------
// matched=false → no rule matched, traffic uses the default (first) outbound
// and outboundTag is empty. groupTags lists the balancer chain, if any.

@Serializable
data class RouteTestResult(
    val matched: Boolean = false,
    val outboundTag: String = "",
    val groupTags: List<String> = emptyList(),
)

// ---- Nodes (GET /panel/api/nodes/list) -----------------------------------

@Serializable
data class Node(
    val id: Int = 0,
    val name: String = "",
    val remark: String = "",
    val scheme: String = "https",
    val address: String = "",
    val port: Int = 0,
    val basePath: String = "/",
    val apiToken: String = "",
    val enable: Boolean = true,
    val allowPrivateAddress: Boolean = false,
    val tlsVerifyMode: String = "verify",
    val pinnedCertSha256: String = "",
    // Route the panel→node API connection through this Xray outbound tag
    // (empty = direct). Panel v3.4.0.
    val outboundTag: String = "",
    // "all" or "selected" (only inboundTags are synced); the update overwrites both.
    val inboundSyncMode: String = "all",
    val inboundTags: List<String> = emptyList(),
    // The node's stable panelGuid — used to attribute online clients to the
    // server that physically hosts them (panel v3.4.0). Empty on older panels.
    val guid: String = "",
    val status: String = "",
    val latencyMs: Long = 0,
    val xrayVersion: String = "",
    val panelVersion: String = "",
    val cpuPct: Double = 0.0,
    val memPct: Double = 0.0,
    val uptimeSecs: Long = 0,
    val lastError: String = "",
    val inboundCount: Int = 0,
    val clientCount: Int = 0,
) {
    val online: Boolean get() = status.equals("online", ignoreCase = true)

    fun toModel(): NodeModel = NodeModel(
        id = id, name = name, remark = remark, scheme = scheme, address = address,
        // The panel never returns a node's token, so an edit leaves the stored one alone.
        port = port, basePath = basePath, apiToken = apiToken.ifBlank { null }, enable = enable,
        allowPrivateAddress = allowPrivateAddress, tlsVerifyMode = tlsVerifyMode,
        pinnedCertSha256 = pinnedCertSha256, outboundTag = outboundTag,
        inboundSyncMode = inboundSyncMode, inboundTags = inboundTags,
    )
}

@Serializable
data class NodeModel(
    val id: Int = 0,
    val name: String = "",
    val remark: String = "",
    val scheme: String = "https",
    val address: String = "",
    val port: Int = 443,
    val basePath: String = "/",
    /** Null on an edit keeps the node's stored token (the key is left out): panel 3.8.0
     *  reads a present empty string as a new, empty token. */
    val apiToken: String? = null,
    val enable: Boolean = true,
    val allowPrivateAddress: Boolean = false,
    val tlsVerifyMode: String = "verify",
    val pinnedCertSha256: String = "",
    val inboundSyncMode: String = "all",
    val inboundTags: List<String> = emptyList(),
    val outboundTag: String = "",
)

/** Response of POST /panel/api/nodes/mtls/ca — this panel's CA cert (PEM). */
@Serializable
data class MtlsCaResponse(val caCert: String = "")

/** Body for POST /panel/api/nodes/mtls/trustCA — the CA whose client certs this
 *  panel trusts (when it acts as a node); empty disables it. Applied on restart. */
@Serializable
data class MtlsTrustCaRequest(val caCert: String)

/** Body for POST /panel/api/nodes/updatePanel — node ids to self-update.
 *  `dev=true` installs the rolling dev-latest build instead of the stable release. */
@Serializable
data class NodeIdsRequest(val ids: List<Int>, val dev: Boolean = false)

// ---- Client bulk / IP-log / export-import (panel v3.4.x) -----------------

/** Body for POST /panel/api/clients/bulkEnable | bulkDisable. */
@Serializable
data class BulkEmailsRequest(val emails: List<String>)

/** Body for POST /panel/api/clients/bulkAdjust. Shifts each client's expiry by
 *  [addDays] and traffic limit by [addBytes] (both may be negative); [flow] sets
 *  the XTLS flow — "" leaves it, "none" clears it, vision values set it.
 *
 *  Panel v3.8.0 adds [limitHwid] — null leaves every device limit alone (sharedJson
 *  drops nulls, so the key is not sent) — and [adTag]: "" leaves the MTProto sponsor
 *  tag alone, "none" clears it, a 32-hex tag sets it (MTProto inbounds only). Older
 *  panels silently ignore both keys, so the dialog offers them on 3.8.0+ only. */
@Serializable
data class BulkAdjustRequest(
    val emails: List<String>,
    val addDays: Int = 0,
    val addBytes: Long = 0,
    val flow: String = "",
    val limitHwid: Int? = null,
    val adTag: String = "",
)

/** Result of POST /panel/api/clients/bulkAdjust: how many clients changed, and why
 *  the rest were skipped (same {email, reason} entries as the bulk delete report). */
@Serializable
data class BulkAdjustResult(val adjusted: Int = 0, val skipped: List<BulkDeleteSkip> = emptyList())

private val BULK_AD_TAG = Regex("^[0-9a-fA-F]{32}$")

/** The bulk-adjust MTProto ad tag as the panel accepts it: empty (no change),
 *  "none" (clear) or exactly 32 hex characters. */
fun isValidBulkAdTag(value: String): Boolean =
    value.isEmpty() || value == "none" || BULK_AD_TAG.matches(value)

// ---- Encrypted Happ link (POST /panel/api/clients/happLink/:id, v3.8.0) ---

/** The client's subscription URL encrypted into a `happ://crypt5/…` link that only
 *  the Happ app opens. The panel builds it on demand and stores nothing, so every
 *  call can return a different link for the same subscription. */
@Serializable
data class HappLinkResult(val encryptedLink: String = "")

/** The panel's exact message when the subscription URL is over its 8192-byte limit. */
const val HAPP_SOURCE_TOO_LONG = "happ_source_too_long"

/** QR version 40 at error-correction level M holds at most this many bytes. */
const val QR_MAX_BYTES = 2331

/** Body for POST /panel/api/clients/bulkDel. */
@Serializable
data class BulkDelRequest(val emails: List<String>, val keepTraffic: Boolean = false)

/** Result of POST /panel/api/clients/bulkDel: how many went, and why the rest did not. */
@Serializable
data class BulkDeleteResult(val deleted: Int = 0, val skipped: List<BulkDeleteSkip> = emptyList())

@Serializable
data class BulkDeleteSkip(val email: String = "", val reason: String = "")

// ---- Client groups (/panel/api/clients/groups…, panel v3.3.0+) ------------

/** A group as GET /panel/api/clients/groups lists it — empty groups included.
 *  [up]/[down] arrived in panel 3.3.1 (null on 3.3.0). The traffic counts from the
 *  group's last reset; each client's own counters are separate. */
@Serializable
data class ClientGroup(
    val name: String = "",
    val clientCount: Int = 0,
    val trafficUsed: Long = 0,
    val up: Long? = null,
    val down: Long? = null,
)

/** Body for POST /panel/api/clients/groups/create | delete | resetTraffic. */
@Serializable
data class GroupNameRequest(val name: String)

/** Body for POST /panel/api/clients/groups/rename. */
@Serializable
data class GroupRenameRequest(val oldName: String, val newName: String)

/** Body for POST /panel/api/clients/groups/bulkAdd — the panel creates [group] if missing. */
@Serializable
data class GroupAddClientsRequest(val emails: List<String>, val group: String)

/** Body for POST /panel/api/clients/import — [data] is the stringified JSON array
 *  of {client, inboundIds} entries (the same shape GET /clients/export returns). */
@Serializable
data class ClientImportRequest(val data: String)

/** One entry of a client's IP log (POST /panel/api/clients/ips/:email).
 *  [node] is the node the IP connects through, or "" on the local panel. */
@Serializable
data class ClientIpInfo(val ip: String = "", val time: String = "", val node: String = "")

// ---- VLESS encryption keygen (GET /panel/api/server/getNewVlessEnc) ------

/**
 * One generated VLESS-encryption key option. The panel runs `xray vlessenc` and
 * derives xorpub/random variants, so each [label] is e.g. "X25519, native" with
 * a ready decryption/encryption pair. (panel v3.4.1, xray-core v26.6.21+)
 */
@Serializable
data class VlessEncAuth(
    val id: String = "",
    val label: String = "",
    val decryption: String = "",
    val encryption: String = "",
)

/** Envelope `obj` of GET /panel/api/server/getNewVlessEnc. */
@Serializable
data class VlessEncResponse(val auths: List<VlessEncAuth> = emptyList())

// ---- Panel update (GET /panel/api/server/getUpdateInfo) ------------------

@Serializable
data class PanelUpdateInfo(
    val currentVersion: String = "",
    val latestVersion: String = "",
    val updateAvailable: Boolean = false,
)

/**
 * The subscription server's `?format=info` snapshot (panel v3.6.0+): the same
 * live status a subscriber's own client app sees when it refreshes the link,
 * minus the config links. Fetched best-effort from the public sub URL, so the
 * app can show the customer's-eye view next to the share QR.
 *
 * Only the human-readable status fields are modeled; the endpoint returns more
 * (byte counters, sub URLs, announce, …) which lenient parsing drops.
 */
@Serializable
data class SubInfo(
    val isOnline: Boolean = false,
    val enabled: Boolean = true,
    val used: String = "",
    val total: String = "",
    val remained: String = "",
    val download: String = "",
    val upload: String = "",
)

/**
 * A remote outbound list the panel fetches and merges into the Xray config
 * (`GET /panel/api/xray/outbound-subs`) — the panel's "Subscriptions" button on
 * the Outbounds page.
 *
 * `lastFetchedOutbounds` (the cached outbound JSON) is deliberately not modelled:
 * it is large and the app reads the outbounds through refresh/preview instead.
 */
@Serializable
data class OutboundSubscription(
    val id: Int = 0,
    val remark: String = "",
    val url: String = "",
    val enabled: Boolean = true,
    /** Prefix given to every imported outbound tag, e.g. `hk-`. */
    val tagPrefix: String = "",
    /** Seconds between automatic refreshes; the panel defaults to 600. */
    val updateInterval: Int = 600,
    /** Order among subscriptions in the merged config (lower = earlier). */
    val priority: Int = 0,
    /** Place this subscription's outbounds before the manually defined ones. */
    val prepend: Boolean = false,
    /** Permit a private/LAN subscription URL — off by default (SSRF guard). */
    val allowPrivate: Boolean = false,
    /** Skip TLS verification when fetching the subscription URL. */
    val allowInsecure: Boolean = false,
    /** Unix ms of the last successful fetch; 0 = never fetched. */
    val lastUpdated: Long = 0,
    /** Message from the last failed fetch, empty when the last one succeeded. */
    val lastError: String = "",
    /** How many outbounds the last fetch produced. */
    val outboundCount: Int = 0,
)

/**
 * One device registered against a client's subscription (panel v3.7.0). The
 * panel counts these against the client's HWID limit; removing one just makes
 * that device register again on its next subscription fetch.
 */
@Serializable
data class ClientHwid(
    val id: Int = 0,
    val subId: String = "",
    val firstSeen: Long = 0,
    val lastSeen: Long = 0,
    val userAgent: String = "",
    val deviceOs: String = "",
    val osVersion: String = "",
    val deviceModel: String = "",
    // Panel v3.8.0: the first 12 characters of the stored HWID hash — enough to
    // tell two similar devices apart without exposing the hash itself.
    val fingerprint: String = "",
) {
    /** What to show as the device's name: model, else OS, else the user agent. */
    val label: String
        get() = listOf(deviceModel, deviceOs, osVersion)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { userAgent }
}

/**
 * What an API token is allowed to reach (panel v3.7.0). A panel older than that
 * has no scopes and treats every token as [ADMIN], which is why that is the
 * default everywhere.
 */
object ApiTokenScope {
    const val ADMIN = "admin"
    const val MONITOR = "monitor"
    const val NODE_SYNC = "node-sync"
    const val NODE_ADMIN = "node-admin"

    val ALL = listOf(ADMIN, MONITOR, NODE_SYNC, NODE_ADMIN)
}
