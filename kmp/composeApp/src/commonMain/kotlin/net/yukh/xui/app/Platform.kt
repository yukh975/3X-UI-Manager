package net.yukh.xui.app

/** App version string (CFBundleShortVersionString on iOS). */
expect fun appVersionName(): String

/** Format a Unix-ms timestamp as "dd.MM" (local). Empty string for 0. */
expect fun formatDayMonth(epochMs: Long): String
