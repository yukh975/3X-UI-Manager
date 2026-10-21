package net.yukh.xui.app

import kotlin.test.Test
import kotlin.test.assertEquals

class I18nTest {

    @Test
    fun chineseDeviceGetsChineseByDefault() {
        assertEquals(LANG_ZH, resolveLanguage(LANG_SYSTEM, "zh"))
    }

    @Test
    fun explicitChineseWinsOverDeviceLanguage() {
        assertEquals(LANG_ZH, resolveLanguage(LANG_ZH, "en"))
        assertEquals(LANG_ZH, resolveLanguage(LANG_ZH, "ru"))
    }

    @Test
    fun trLooksChineseUpInZhStrings() {
        assertEquals(zhStrings.getValue("Retry"), tr(LANG_ZH, "Retry"))
    }

    // A string added to one language only silently shows English to the other's users.
    @Test
    fun everyTranslatedStringExistsInBothRussianAndChinese() {
        assertEquals(emptyList(), (ruStrings.keys - zhStrings.keys).sorted(), "missing from zhStrings")
        assertEquals(emptyList(), (zhStrings.keys - ruStrings.keys).sorted(), "missing from ruStrings")
    }
}
