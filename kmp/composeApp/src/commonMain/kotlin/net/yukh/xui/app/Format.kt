package net.yukh.xui.app

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
