package net.yukh.xui.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationParityTest {

    // A string added to one language only silently shows English to the other's users.
    @Test
    fun `every translated string exists in both Russian and Chinese`() {
        assertEquals("missing from zhStrings", emptyList<String>(), (ruStrings.keys - zhStrings.keys).sorted())
        assertEquals("missing from ruStrings", emptyList<String>(), (zhStrings.keys - ruStrings.keys).sorted())
    }
}
