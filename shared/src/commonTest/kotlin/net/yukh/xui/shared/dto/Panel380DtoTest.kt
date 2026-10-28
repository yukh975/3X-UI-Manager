package net.yukh.xui.shared.dto

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.yukh.xui.shared.api.sharedJson

/** Wire shapes the panel v3.8.0 support relies on, read and written with the same
 *  Json settings PanelApi uses. */
class Panel380DtoTest {

    @Test
    fun deviceListReadsTheShortFingerprintAndToleratesOlderPanels() {
        val new = sharedJson.decodeFromString(ClientHwid.serializer(), """{"id":7,"deviceModel":"Pixel 8","fingerprint":"3fa2c1d09b7e"}""")
        val old = sharedJson.decodeFromString(ClientHwid.serializer(), """{"id":8,"deviceModel":"iPhone"}""")
        assertEquals("3fa2c1d09b7e", new.fingerprint)
        assertEquals("", old.fingerprint)
    }

    @Test
    fun bulkAdjustLeavesTheDeviceLimitOutUnlessOneIsSet() {
        val untouched = sharedJson.encodeToJsonElement(
            BulkAdjustRequest.serializer(),
            BulkAdjustRequest(listOf("a"), addDays = 3),
        ).jsonObject
        assertFalse("limitHwid" in untouched, "limitHwid must be omitted so the panel leaves limits alone")
        assertEquals("", untouched.getValue("adTag").jsonPrimitive.content)

        val set = sharedJson.encodeToJsonElement(
            BulkAdjustRequest.serializer(),
            BulkAdjustRequest(listOf("a"), limitHwid = 0, adTag = "none"),
        ).jsonObject
        assertEquals(0, set.getValue("limitHwid").jsonPrimitive.int)
        assertEquals("none", set.getValue("adTag").jsonPrimitive.content)
    }

    @Test
    fun bulkAdjustReportParsesCountsSkipReasonsAndAnOmittedSkipList() {
        val serializer = ApiResponse.serializer(BulkAdjustResult.serializer())
        val withSkips = sharedJson.decodeFromString(
            serializer,
            """{"success":true,"msg":"","obj":{"adjusted":2,"skipped":[{"email":"c","reason":"adTag not supported on inbound"}]}}""",
        )
        assertEquals(2, withSkips.obj?.adjusted)
        assertEquals("adTag not supported on inbound", withSkips.obj?.skipped?.single()?.reason)

        // The panel drops an empty skip list (omitempty).
        val clean = sharedJson.decodeFromString(serializer, """{"success":true,"msg":"","obj":{"adjusted":3}}""")
        assertEquals(emptyList<BulkDeleteSkip>(), clean.obj?.skipped)

        val bareAck = sharedJson.decodeFromString(serializer, """{"success":true,"msg":""}""")
        assertNull(bareAck.obj)
    }

    @Test
    fun adTagValidationMatchesThePanel() {
        assertTrue(isValidBulkAdTag(""))
        assertTrue(isValidBulkAdTag("none"))
        assertTrue(isValidBulkAdTag("0123456789abcdef0123456789ABCDEF"))
        assertFalse(isValidBulkAdTag("0123456789abcdef"))
        assertFalse(isValidBulkAdTag("zz23456789abcdef0123456789abcdef"))
    }

    @Test
    fun happLinkResultAndTheTooLongRefusalParse() {
        val serializer = ApiResponse.serializer(HappLinkResult.serializer())
        val ok = sharedJson.decodeFromString(serializer, """{"success":true,"msg":"","obj":{"encryptedLink":"happ://crypt5/abc"}}""")
        assertEquals("happ://crypt5/abc", ok.obj?.encryptedLink)

        val tooLong = sharedJson.decodeFromString(serializer, """{"success":false,"msg":"happ_source_too_long","obj":null}""")
        assertFalse(tooLong.success)
        assertEquals(HAPP_SOURCE_TOO_LONG, tooLong.msg)
        assertNull(tooLong.obj)
    }

    @Test
    fun happLinkSwitchParsesAndIsOffOnOlderPanels() {
        val on = sharedJson.decodeFromString(PanelSubSettings.serializer(), """{"subEnable":true,"happLinkEnable":true}""")
        val old = sharedJson.decodeFromString(PanelSubSettings.serializer(), """{"subEnable":true}""")
        assertTrue(on.happLinkEnable)
        assertFalse(old.happLinkEnable)
    }

    @Test
    fun amneziawgOutboundSkeletonMatchesTheAndroidApp() {
        val ob = Json.parseToJsonElement(defaultOutbound("amneziawg", "awg-out")).jsonObject
        assertEquals("amneziawg", ob.getValue("protocol").jsonPrimitive.content)
        assertEquals("awg-out", ob.getValue("tag").jsonPrimitive.content)
        val settings = ob.getValue("settings").jsonObject
        assertEquals("", settings.getValue("secretKey").jsonPrimitive.content)
        assertEquals(listOf("10.8.0.2/32"), settings.getValue("address").jsonArray.map { it.jsonPrimitive.content })
        val peer = settings.getValue("peers").jsonArray.single().jsonObject
        assertEquals("", peer.getValue("publicKey").jsonPrimitive.content)
        assertEquals("", peer.getValue("endpoint").jsonPrimitive.content)
        assertEquals(listOf("0.0.0.0/0", "::/0"), peer.getValue("allowedIPs").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(25, peer.getValue("keepAlive").jsonPrimitive.int)
    }

    @Test
    fun keepAliveIsWrittenOnlyWhenItMeansSomething() {
        fun body(model: ClientModel) = sharedJson.encodeToJsonElement(ClientModel.serializer(), model).jsonObject

        // A VLESS-only row has no keepalive, so the payload rebuilt from it must not add one.
        val vless = sharedJson.decodeFromString(Client.serializer(), """{"id":4,"email":"v","uuid":"u","inboundIds":[1]}""")
        assertFalse("keepAlive" in body(vless.toModel()), "keepAlive must be left out for a non-tunnel client")

        val wg = sharedJson.decodeFromString(Client.serializer(), """{"id":5,"email":"wg","keepAlive":25,"allowedIPs":"10.0.0.5/32"}""")
        assertEquals(25, body(wg.toModel()).getValue("keepAlive").jsonPrimitive.int)

        // The editor's explicit 0 for a tunnel client means "off" and must reach the panel.
        assertEquals(0, body(wg.toModel().copy(keepAlive = "0")).getValue("keepAlive").jsonPrimitive.int)
    }

    @Test
    fun amneziawgKeepAliveRangeSurvivesTheListAndTheWriteBack() {
        val list = sharedJson.decodeFromString(
            ListSerializer(Client.serializer()),
            """[{"id":1,"email":"wg","keepAlive":25},{"id":2,"email":"awg","keepAlive":"15-25"}]""",
        )
        assertEquals("25", list[0].keepAlive)
        assertEquals("15-25", list[1].keepAlive)

        fun sent(model: ClientModel) = sharedJson.encodeToJsonElement(ClientModel.serializer(), model).jsonObject.getValue("keepAlive")
        assertEquals(JsonPrimitive(25), sent(list[0].toModel()))
        assertEquals(JsonPrimitive("15-25"), sent(list[1].toModel()))
    }
}
