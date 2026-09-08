package net.yukh.xui.app

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDateComponents
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneWithAbbreviation
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

actual fun appVersionName(): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "—"

private val dayMonthFormatter = NSDateFormatter().apply { dateFormat = "dd.MM" }

actual fun formatDayMonth(epochMs: Long): String =
    if (epochMs <= 0L) "" else dayMonthFormatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0))

private val dateTimeFormatter = NSDateFormatter().apply { dateFormat = "yyyy-MM-dd HH:mm" }

actual fun formatDateTime(epochMs: Long): String =
    if (epochMs <= 0L) "" else dateTimeFormatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0))

actual fun combineDateAndTime(dayUtcMs: Long, hour: Int, minute: Int): Long {
    val day = NSDate.dateWithTimeIntervalSince1970(dayUtcMs / 1000.0)
    val utcCalendar = NSCalendar.currentCalendar.apply {
        timeZone = NSTimeZone.timeZoneWithAbbreviation("UTC") ?: timeZone
    }
    val parts = utcCalendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay,
        day,
    )
    val local = NSDateComponents().apply {
        year = parts.year
        month = parts.month
        this.day = parts.day
        this.hour = hour.toLong()
        this.minute = minute.toLong()
        second = 0
    }
    val date = NSCalendar.currentCalendar.dateFromComponents(local) ?: return dayUtcMs
    return (date.timeIntervalSince1970 * 1000.0).toLong()
}

actual fun localHourMinute(epochMs: Long): Pair<Int, Int> {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0)
    val parts = NSCalendar.currentCalendar.components(NSCalendarUnitHour or NSCalendarUnitMinute, date)
    return parts.hour.toInt() to parts.minute.toInt()
}
