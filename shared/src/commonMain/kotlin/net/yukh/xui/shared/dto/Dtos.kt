package net.yukh.xui.shared.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
 * fields like up/down/tag/clientStats are dropped by ignoreUnknownKeys).
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
    val quota: Long get() = traffic?.total?.takeIf { it > 0 } ?: totalGB
    val lastOnline: Long get() = traffic?.lastOnline ?: 0

    fun toModel(): ClientModel = ClientModel(
        id = uuid, email = email, password = password, auth = auth, security = security,
        secret = secret, adTag = adTag,
        privateKey = privateKey, publicKey = publicKey, preSharedKey = preSharedKey,
        allowedIPs = allowedIPs.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        keepAlive = keepAlive,
        flow = flow, limitIp = limitIp, totalGB = totalGB, expiryTime = expiryTime,
        enable = enable, tgId = tgId, subId = subId, group = group, comment = comment,
        reset = reset, createdAt = createdAt, updatedAt = updatedAt,
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
    val keepAlive: Int = 0,
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
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

@Serializable
data class ClientCreatePayload(val client: ClientModel, val inboundIds: List<Int>)

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
        port = port, basePath = basePath, apiToken = apiToken, enable = enable,
        allowPrivateAddress = allowPrivateAddress, tlsVerifyMode = tlsVerifyMode,
        pinnedCertSha256 = pinnedCertSha256, outboundTag = outboundTag,
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
    val apiToken: String = "",
    val enable: Boolean = true,
    val allowPrivateAddress: Boolean = false,
    val tlsVerifyMode: String = "verify",
    val pinnedCertSha256: String = "",
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
 *  the XTLS flow — "" leaves it, "none" clears it, vision values set it. */
@Serializable
data class BulkAdjustRequest(
    val emails: List<String>,
    val addDays: Int = 0,
    val addBytes: Long = 0,
    val flow: String = "",
)

/** Body for POST /panel/api/clients/bulkDel. */
@Serializable
data class BulkDelRequest(val emails: List<String>, val keepTraffic: Boolean = false)

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
