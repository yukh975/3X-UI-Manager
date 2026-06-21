package net.yukh.xui.ui.format

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import net.yukh.xui.i18n.LANG_EN
import net.yukh.xui.i18n.LANG_RU
import net.yukh.xui.i18n.tr

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
fun Long.formatExpiry(lang: String = LANG_EN): String {
    if (this == 0L) return tr(lang, "Never")
    val now = System.currentTimeMillis()
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(this))
    return if (this < now) tr(lang, "Expired") + " " + date else date
}

/** Plain calendar date for an expiry timestamp (Unix ms). 0 → "Never". */
fun Long.formatDate(lang: String = LANG_EN): String =
    if (this == 0L) tr(lang, "Never") else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(this))

/** Short day.month for a period start, e.g. "01.06" (Unix ms). 0 → "". */
fun Long.formatDayMonth(): String =
    if (this <= 0L) "" else SimpleDateFormat("dd.MM", Locale.US).format(Date(this))

/** Days remaining until expiry (Unix ms). 0 → "Never"; already past → "Expired";
 *  otherwise the whole-day count with a short unit ("12 d" / "12 дн."). */
fun Long.formatExpiryDays(lang: String = LANG_EN): String {
    if (this == 0L) return tr(lang, "Never")
    val days = (this - System.currentTimeMillis()) / 86_400_000L
    if (days < 0) return tr(lang, "Expired")
    val unit = if (lang == LANG_RU) "дн." else "d"
    return "$days $unit"
}

/** Format a relative timestamp ("12s ago", "5m ago"). 0 → "—". */
fun Long.formatLastOnline(lang: String = LANG_EN): String {
    if (this <= 0L) return "—"
    val elapsedMs = System.currentTimeMillis() - this
    if (elapsedMs < 0) return tr(lang, "now")
    val seconds = elapsedMs / 1_000
    return when {
        seconds < 60 -> "${seconds}${tr(lang, "s ago")}"
        seconds < 3_600 -> "${seconds / 60}${tr(lang, "m ago")}"
        seconds < 86_400 -> "${seconds / 3_600}${tr(lang, "h ago")}"
        else -> "${seconds / 86_400}${tr(lang, "d ago")}"
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
