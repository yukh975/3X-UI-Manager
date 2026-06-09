package net.yukh.xui.app

import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

actual fun appVersionName(): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "—"

private val dayMonthFormatter = NSDateFormatter().apply { dateFormat = "dd.MM" }

actual fun formatDayMonth(epochMs: Long): String =
    if (epochMs <= 0L) "" else dayMonthFormatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0))
