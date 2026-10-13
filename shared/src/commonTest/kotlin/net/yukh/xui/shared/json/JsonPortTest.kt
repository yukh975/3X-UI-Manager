package net.yukh.xui.shared.json

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonPortTest {
    @Test
    fun portIsWrittenAsNumber() {
        val out = jsonPutPort("{\"settings\":{}}", listOf("settings", "port"), "443")
        assertEquals("443", jsonGetString(out, listOf("settings", "port")))
        assertEquals(false, out.contains("\"443\""))
    }

    @Test
    fun blankPortRemovesKey() {
        val out = jsonPutPort("{\"port\":53}", listOf("port"), "")
        assertEquals(false, out.contains("port"))
    }
}
