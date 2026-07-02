package net.yukh.xui.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun appVersionName(): String = "0.7.2"

private val dayMonth = SimpleDateFormat("dd.MM", Locale.getDefault())

actual fun formatDayMonth(epochMs: Long): String =
    if (epochMs <= 0L) "" else dayMonth.format(Date(epochMs))
