package net.yukh.xui.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

/** "Follow the device language" is the default, so this decides what a user
 *  sees on first launch — worth pinning down. */
class LanguageResolveTest {

    @Test
    fun `system default follows the device language`() {
        assertEquals(LANG_RU, resolveLanguage(LANG_SYSTEM, "ru"))
        assertEquals(LANG_EN, resolveLanguage(LANG_SYSTEM, "en"))
    }

    @Test
    fun `an untranslated device language falls back to English`() {
        assertEquals(LANG_EN, resolveLanguage(LANG_SYSTEM, "de"))
        assertEquals(LANG_EN, resolveLanguage(LANG_SYSTEM, ""))
    }

    @Test
    fun `an explicit choice wins over the device language`() {
        assertEquals(LANG_EN, resolveLanguage(LANG_EN, "ru"))
        assertEquals(LANG_RU, resolveLanguage(LANG_RU, "en"))
    }

    @Test
    fun `an unknown stored value behaves like system default`() {
        assertEquals(LANG_RU, resolveLanguage("zz", "ru"))
        assertEquals(LANG_EN, resolveLanguage("zz", "fr"))
    }
}
