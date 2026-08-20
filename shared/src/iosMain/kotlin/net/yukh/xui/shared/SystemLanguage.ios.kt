package net.yukh.xui.shared

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

// preferredLanguages entries are BCP-47 tags ("ru-RU", "en-GB"); we only care
// about the language subtag.
actual fun systemLanguage(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)
        ?.substringBefore('-')
        ?.lowercase()
        .orEmpty()
