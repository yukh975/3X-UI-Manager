package net.yukh.xui.shared.dto

import kotlinx.serialization.Serializable

/**
 * One sample from the system-metrics history
 * (GET /panel/api/server/history/{metric}/{bucket}): [t] is a Unix timestamp in
 * seconds, [v] the aggregated value for that bucket (percent / ratio /
 * bytes-per-second / count, depending on the metric).
 */
@Serializable
data class MetricPoint(val t: Long = 0, val v: Double = 0.0)
