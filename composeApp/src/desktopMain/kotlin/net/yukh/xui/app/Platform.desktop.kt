package net.yukh.xui.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun appVersionName(): String = "0.12.2"

private val dayMonth = SimpleDateFormat("dd.MM", Locale.getDefault())

actual fun formatDayMonth(epochMs: Long): String =
    if (epochMs <= 0L) "" else dayMonth.format(Date(epochMs))

private val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

actual fun formatDateTime(epochMs: Long): String =
    if (epochMs <= 0L) "" else dateTime.format(Date(epochMs))

actual fun combineDateAndTime(dayUtcMs: Long, hour: Int, minute: Int): Long {
    val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = dayUtcMs }
    val local = java.util.Calendar.getInstance()
    local.set(
        utc.get(java.util.Calendar.YEAR),
        utc.get(java.util.Calendar.MONTH),
        utc.get(java.util.Calendar.DAY_OF_MONTH),
        hour,
        minute,
        0,
    )
    local.set(java.util.Calendar.MILLISECOND, 0)
    return local.timeInMillis
}

actual fun localHourMinute(epochMs: Long): Pair<Int, Int> {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
    return c.get(java.util.Calendar.HOUR_OF_DAY) to c.get(java.util.Calendar.MINUTE)
}

actual fun localZoneLabel(epochMs: Long): String {
    val offset = java.util.TimeZone.getDefault().getOffset(if (epochMs > 0) epochMs else System.currentTimeMillis())
    val sign = if (offset < 0) "-" else "+"
    val minutes = kotlin.math.abs(offset) / 60_000
    return "UTC%s%02d:%02d".format(sign, minutes / 60, minutes % 60)
}

actual fun formatDateTimeInZone(epochMs: Long, zoneId: String): String? {
    if (epochMs <= 0L || zoneId.isBlank() || zoneId.equals("Local", ignoreCase = true)) return null
    val zone = java.util.TimeZone.getTimeZone(zoneId)
    if (zone.id != zoneId) return null
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone }.format(Date(epochMs))
}
