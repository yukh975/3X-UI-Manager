package net.yukh.xui.ui.format

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

private val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

/** Pretty-print a byte count as e.g. "1.4 GB". 0 → "0 B"; negative → absolute. */
fun Long.formatBytes(): String {
    val abs = if (this < 0) -this else this
    if (abs == 0L) return "0 B"
    val exp = (ln(abs.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = abs / 1024.0.pow(exp.toDouble())
    return "%.1f %s".format(Locale.US, value, units[exp])
}

/** Format an inbound/client expiry timestamp (Unix ms). 0 → "Never". */
fun Long.formatExpiry(): String {
    if (this == 0L) return "Never"
    val now = System.currentTimeMillis()
    return if (this < now) "Expired " + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(this))
    else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(this))
}

/** Plain calendar date for an expiry timestamp (Unix ms). 0 → "Never". */
fun Long.formatDate(): String =
    if (this == 0L) "Never" else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(this))

/** Format a relative timestamp ("12s ago", "5m ago"). 0 → "—". */
fun Long.formatLastOnline(): String {
    if (this <= 0L) return "—"
    val elapsedMs = System.currentTimeMillis() - this
    if (elapsedMs < 0) return "now"
    val seconds = elapsedMs / 1_000
    return when {
        seconds < 60 -> "${seconds}s ago"
        seconds < 3_600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3_600}h ago"
        else -> "${seconds / 86_400}d ago"
    }
}

/** "5.5%" rounded to one decimal. */
fun Double.formatPercent(): String = "%.1f%%".format(Locale.US, this)

/** Format an uptime given in seconds as "12d 3h 5m". */
fun Long.formatUptime(): String {
    if (this <= 0) return "—"
    val days = this / 86_400
    val hours = (this % 86_400) / 3_600
    val minutes = (this % 3_600) / 60
    return buildList {
        if (days > 0) add("${days}d")
        if (hours > 0) add("${hours}h")
        if (minutes > 0 || isEmpty()) add("${minutes}m")
    }.joinToString(" ")
}
