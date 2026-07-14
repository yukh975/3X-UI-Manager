package net.yukh.xui.app

import androidx.compose.runtime.compositionLocalOf

/** Pretty-print a byte count, e.g. "1.4 GB". Pure-Kotlin (no java.text). */
fun Long.formatBytes(): String {
    if (this <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB", "PB")
    var value = this.toDouble()
    var i = 0
    while (value >= 1024.0 && i < units.lastIndex) {
        value /= 1024.0
        i++
    }
    val rounded = (value * 10).toLong() / 10.0
    return "$rounded ${units[i]}"
}

private val bitUnits = listOf("bit/s", "Kbit/s", "Mbit/s", "Gbit/s", "Tbit/s")

/** Whether live speeds render in bits/s. Provided at the root from user prefs. */
val LocalSpeedInBits = compositionLocalOf { false }

/** Format a bytes/second rate: bytes → "12.0 KB/s" (1024-based, matches
 *  [formatBytes]); bits → "96.0 Kbit/s" (×8, 1000-based, network convention). */
fun formatSpeed(bytesPerSec: Long, bits: Boolean): String {
    if (!bits) return "${bytesPerSec.formatBytes()}/s"
    var v = (if (bytesPerSec < 0) 0L else bytesPerSec) * 8.0
    if (v == 0.0) return "0 bit/s"
    var i = 0
    while (v >= 1000.0 && i < bitUnits.lastIndex) {
        v /= 1000.0
        i++
    }
    val rounded = (v * 10).toLong() / 10.0
    return "$rounded ${bitUnits[i]}"
}

/** One-decimal percent, e.g. "51.5%". */
fun Double.formatPercent(): String {
    val rounded = (this * 10).toLong() / 10.0
    return "$rounded%"
}

/** Uptime in seconds → "12d 3h 5m". */
fun Long.formatUptime(): String {
    if (this <= 0L) return "—"
    val days = this / 86_400
    val hours = (this % 86_400) / 3_600
    val minutes = (this % 3_600) / 60
    return buildList {
        if (days > 0) add("${days}d")
        if (hours > 0) add("${hours}h")
        if (minutes > 0 || isEmpty()) add("${minutes}m")
    }.joinToString(" ")
}
