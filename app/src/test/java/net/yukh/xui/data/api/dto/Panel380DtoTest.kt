package net.yukh.xui.data.api.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wire shapes the panel v3.8.0 support relies on, read and written with the
 *  same Json settings the app's network module uses. */
class Panel380DtoTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun `device list reads the short fingerprint and tolerates older panels`() {
        val new = json.decodeFromString(ClientHwid.serializer(), """{"id":7,"deviceModel":"Pixel 8","fingerprint":"3fa2c1d09b7e"}""")
        val old = json.decodeFromString(ClientHwid.serializer(), """{"id":8,"deviceModel":"iPhone"}""")
        assertEquals("3fa2c1d09b7e", new.fingerprint)
        assertEquals("", old.fingerprint)
    }

    @Test
    fun `bulk adjust leaves the device limit out unless one is set`() {
        val untouched = json.encodeToJsonElement(BulkAdjustRequest.serializer(), BulkAdjustRequest(listOf("a"), addDays = 3)).jsonObject
        assertFalse("limitHwid must be omitted so the panel leaves limits alone", "limitHwid" in untouched)
        assertEquals("\"\"", untouched["adTag"].toString())

        val set = json.encodeToJsonElement(BulkAdjustRequest.serializer(), BulkAdjustRequest(listOf("a"), limitHwid = 0, adTag = "none")).jsonObject
        assertEquals("0", set["limitHwid"].toString())
        assertEquals("\"none\"", set["adTag"].toString())
    }

    @Test
    fun `bulk adjust report parses adjusted count and skip reasons`() {
        val r = json.decodeFromString(
            BulkAdjustResult.serializer(),
            """{"adjusted":2,"skipped":[{"email":"c","reason":"adTag not supported on inbound"}]}""",
        )
        assertEquals(2, r.adjusted)
        assertEquals("adTag not supported on inbound", r.skipped.single().reason)
    }

    @Test
    fun `ad tag validation matches the panel`() {
        assertTrue(isValidBulkAdTag(""))
        assertTrue(isValidBulkAdTag("none"))
        assertTrue(isValidBulkAdTag("0123456789abcdef0123456789ABCDEF"))
        assertFalse(isValidBulkAdTag("0123456789abcdef"))
        assertFalse(isValidBulkAdTag("zz23456789abcdef0123456789abcdef"))
    }

    @Test
    fun `client writes carry keepAlive only when the client has one`() {
        val vless = json.encodeToJsonElement(ClientModel.serializer(), Client(email = "v", uuid = "u").toModel()).jsonObject
        assertFalse("a stored 0 must stay out so 3.8.0 keeps its value", "keepAlive" in vless)
        val wgModel = Client(email = "w", keepAlive = "25").toModel()
        assertEquals("25", json.encodeToJsonElement(ClientModel.serializer(), wgModel).jsonObject["keepAlive"].toString())
        val off = json.encodeToJsonElement(ClientModel.serializer(), wgModel.copy(keepAlive = "0")).jsonObject
        assertEquals("an explicit 0 from the editor is sent", "0", off["keepAlive"].toString())
    }

    @Test
    fun `an AmneziaWG keepalive range is sent back as the string the panel stored`() {
        val listed = json.decodeFromString(Client.serializer(), """{"email":"a","keepAlive":"15-25"}""")
        val sent = json.encodeToJsonElement(ClientModel.serializer(), listed.toModel()).jsonObject
        assertEquals("\"15-25\"", sent["keepAlive"].toString())
    }

    @Test
    fun `happ link and its settings switch parse`() {
        val link = json.decodeFromString(HappLinkResult.serializer(), """{"encryptedLink":"happ://crypt5/abc"}""")
        assertEquals("happ://crypt5/abc", link.encryptedLink)
        val on = json.decodeFromString(PanelSettings.serializer(), """{"subEnable":true,"happLinkEnable":true}""")
        val old = json.decodeFromString(PanelSettings.serializer(), """{"subEnable":true}""")
        assertTrue(on.happLinkEnable)
        assertFalse(old.happLinkEnable)
    }
}
