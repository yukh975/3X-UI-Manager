package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * GET /panel/api/server/status on 3x-ui v3.x.
 *
 * The payload nests its metrics into sub-objects (mem/swap/disk are
 * {current,total}; xray is {state,…}; net is split into rate vs totals).
 * Every field defaults so a panel on a slightly different minor version
 * doesn't break parsing.
 */
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
    /** True when Xray reports itself running. */
    val xrayRunning: Boolean get() = xray.state.equals("running", ignoreCase = true)

    val load1: Double get() = loads.getOrElse(0) { 0.0 }
    val load5: Double get() = loads.getOrElse(1) { 0.0 }
    val load15: Double get() = loads.getOrElse(2) { 0.0 }

    /** Memory used percentage, 0..100. */
    val memPercent: Double
        get() = if (mem.total > 0) mem.current.toDouble() / mem.total * 100.0 else 0.0

    val diskPercent: Double
        get() = if (disk.total > 0) disk.current.toDouble() / disk.total * 100.0 else 0.0
}

@Serializable
data class ResourceUsage(
    val current: Long = 0,
    val total: Long = 0,
)

@Serializable
data class XrayStatus(
    val state: String = "",
    val errorMsg: String = "",
    val version: String = "",
)

@Serializable
data class NetIO(
    val up: Long = 0,
    val down: Long = 0,
    val pktUp: Long = 0,
    val pktDown: Long = 0,
)

@Serializable
data class NetTraffic(
    val sent: Long = 0,
    val recv: Long = 0,
)

@Serializable
data class PublicIP(
    val ipv4: String = "",
    val ipv6: String = "",
)

@Serializable
data class AppStats(
    val threads: Int = 0,
    val mem: Long = 0,
    val uptime: Long = 0,
)
