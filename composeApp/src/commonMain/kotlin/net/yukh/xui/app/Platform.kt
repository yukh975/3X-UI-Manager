package net.yukh.xui.app

/** App version string (CFBundleShortVersionString on iOS). */
expect fun appVersionName(): String

/** Format a Unix-ms timestamp as "dd.MM" (local). Empty string for 0. */
expect fun formatDayMonth(epochMs: Long): String

/** Format a Unix-ms timestamp as "yyyy-MM-dd HH:mm" in the device's own time
 *  zone — the panel stores an expiry to the minute, so a date alone would hide
 *  half of what was set. Empty string for 0. */
expect fun formatDateTime(epochMs: Long): String

/**
 * Combine the day a date picker reported (UTC midnight) with a wall-clock time
 * into an instant in the device's own time zone, which is how the panel reads
 * and shows the value. Using the picker's millis as-is would pin every expiry
 * to midnight UTC and, west of Greenwich, land on the previous day.
 */
expect fun combineDateAndTime(dayUtcMs: Long, hour: Int, minute: Int): Long

/** Hour and minute of a timestamp in the device's own time zone. */
expect fun localHourMinute(epochMs: Long): Pair<Int, Int>
