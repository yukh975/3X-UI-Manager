package net.yukh.xui.shared.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** mtg-multi's pendingHandshakesMaxPerIp (panel 3.10.2) as edited from the MTProto inbound form. */
class InboundMtprotoHandshakeLimitTest {

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private val base = """{"clients": [{"email": "a"}], "fakeTlsDomain": "example.com"}"""

    @Test
    fun theLimitIsWrittenAsAJsonNumberBecauseThePanelBindsItToAnInt() {
        val value = obj(mtprotoSettingsWithPendingHandshakes(base, "12")).getValue("pendingHandshakesMaxPerIp").jsonPrimitive
        assertFalse(value.isString)
        assertEquals("12", value.content)
    }

    @Test
    fun clearingTheFieldDropsTheKeyAndKeepsTheRestOfTheSettings() {
        val set = mtprotoSettingsWithPendingHandshakes(base, "12")
        val cleared = obj(mtprotoSettingsWithPendingHandshakes(set, ""))
        assertEquals(setOf("clients", "fakeTlsDomain"), cleared.keys)
        assertEquals(obj(base), cleared)
    }

    @Test
    fun aPastedOverlongNumberIsCappedInsteadOfOverflowingTheIntParse() {
        val value = obj(mtprotoSettingsWithPendingHandshakes(base, "99999999999 per ip")).getValue("pendingHandshakesMaxPerIp")
        assertEquals("999999", value.jsonPrimitive.content)
    }
}
