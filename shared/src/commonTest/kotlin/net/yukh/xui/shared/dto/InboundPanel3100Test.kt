package net.yukh.xui.shared.dto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.yukh.xui.shared.api.sharedJson

/** Inbound editing against panel v3.10.0: SS-2022 keys, MASQUE, carried-through fields. */
@OptIn(ExperimentalEncodingApi::class)
class InboundPanel3100Test {

    private fun keyBytes(key: String) = Base64.decode(key).size
    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun shadowsocks2022KeysHaveTheLengthXrayParseKeyWants() {
        assertEquals(16, keyBytes(InboundTemplates.shadowsocksKey("2022-blake3-aes-128-gcm")))
        assertEquals(32, keyBytes(InboundTemplates.shadowsocksKey("2022-blake3-aes-256-gcm")))
        assertNotEquals(
            InboundTemplates.shadowsocksKey("2022-blake3-aes-256-gcm"),
            InboundTemplates.shadowsocksKey("2022-blake3-aes-256-gcm"),
        )
    }

    @Test
    fun newShadowsocksInboundCarriesAUsableServerKey() {
        val s = obj(InboundTemplates.settings("shadowsocks"))
        assertEquals("2022-blake3-aes-256-gcm", s.getValue("method").jsonPrimitive.content)
        assertEquals(32, keyBytes(s.getValue("password").jsonPrimitive.content))
    }

    @Test
    fun aMethodChangeRegeneratesTheKeyAtTheNewLengthAndKeepsTheRest() {
        val changed = obj(ssSettingsWithMethod(InboundTemplates.settings("shadowsocks"), "2022-blake3-aes-128-gcm"))
        assertEquals("2022-blake3-aes-128-gcm", changed.getValue("method").jsonPrimitive.content)
        assertEquals(16, keyBytes(changed.getValue("password").jsonPrimitive.content))
        assertEquals("tcp,udp", changed.getValue("network").jsonPrimitive.content)
        assertEquals("{ broken", ssSettingsWithMethod("{ broken", "aes-256-gcm"))
    }

    @Test
    fun inboundMethodListLeavesOutTheRefusedChacha2022Method() {
        assertFalse("2022-blake3-chacha20-poly1305" in InboundTemplates.SS_INBOUND_METHODS)
        assertTrue(InboundTemplates.SS_DEFAULT_METHOD in InboundTemplates.SS_INBOUND_METHODS)
    }

    @Test
    fun protocolListOffersMixedInsteadOfSocksAndMasqueOnlyOn3100() {
        val old = InboundTemplates.protocols(panel380 = true, panel3100 = false)
        val new = InboundTemplates.protocols(panel380 = true, panel3100 = true)
        assertTrue("mixed" in old)
        assertFalse("socks" in old)
        assertFalse("masque" in old)
        assertTrue("masque" in new)
    }

    @Test
    fun masqueTemplateIsTheTlsOnlyMasqueStreamWithTheDefaultPool() {
        val stream = obj(InboundTemplates.streamSettings("masque"))
        assertEquals("masque", stream.getValue("network").jsonPrimitive.content)
        assertEquals("tls", stream.getValue("security").jsonPrimitive.content)
        val settings = InboundTemplates.settings("masque")
        assertEquals(listOf("10.14.0.1/24", "fd14::1/64"), obj(settings).getValue("address").jsonArray.map { it.jsonPrimitive.content })
        assertTrue(masqueSettingsValid(settings))
        assertFalse(masqueSettingsValid(masqueSettingsWithMtu(settings, "900")))
        assertTrue(masqueSettingsValid(masqueSettingsWithMtu(settings, "1400")))
        assertFalse(masqueSettingsValid("""{"address": []}"""))
    }

    @Test
    fun masqueXdriveAndTunOnlyGetTheSectionsThatKeepThemWorking() {
        assertEquals(listOf("tls"), InboundCapabilities.securities("masque", "masque"))
        assertFalse(InboundCapabilities.hasSelectableNetwork("masque"))
        assertEquals(listOf("none"), InboundCapabilities.securities("vless", "xdrive"))
        assertFalse(InboundCapabilities.hasStream("tun"))
        assertTrue(InboundCapabilities.hasSniffing("tun"))
        assertEquals(listOf("none", "tls", "reality"), InboundCapabilities.securities("vless", "tcp"))
    }

    @Test
    fun wsTlsGetsHttp11AlpnAndTheCertFieldsLandInTheFirstCertificate() {
        val ws = inboundStreamWithWsTlsAlpn("""{"network":"ws","security":"tls","tlsSettings":{"alpn":["h2","http/1.1"]}}""")
        assertEquals("[\"http/1.1\"]", obj(ws).getValue("tlsSettings").jsonObject["alpn"].toString())
        val tcp = """{"network":"tcp","security":"tls"}"""
        assertEquals(tcp, inboundStreamWithWsTlsAlpn(tcp))

        val withCert = inboundStreamWithTlsCertField(tcp, "certificateFile", "/etc/x.crt")
        assertEquals("/etc/x.crt", inboundTlsCertField(withCert, "certificateFile"))
        assertEquals("", inboundTlsCertField(withCert, "keyFile"))
    }

    @Test
    fun anEditSendsBackTheFieldsThePanelWouldOtherwiseReset() {
        val loaded = sharedJson.decodeFromString(
            InboundModel.serializer(),
            """{"id":7,"remark":"r","port":443,"protocol":"vless","tag":"my-in","subSortIndex":5,
               "excludeFromSub":true,"trafficReset":"monthly","trafficResetDay":15,"clientStats":[],
               "settings":{"clients":[]},"streamSettings":{},"sniffing":{}}""",
        )
        val edited = assertNotNull(
            buildInbound(loaded, "vless", "renamed", "", 8443, 0, "monthly", false, true, "{}", "{}", "{}"),
        )
        val body = sharedJson.encodeToJsonElement(InboundModel.serializer(), edited).jsonObject
        assertEquals("my-in", body.getValue("tag").jsonPrimitive.content)
        assertEquals("5", body.getValue("subSortIndex").toString())
        assertEquals("true", body.getValue("excludeFromSub").toString())
        assertEquals("15", body.getValue("trafficResetDay").toString())
        assertEquals("renamed", body.getValue("remark").jsonPrimitive.content)
    }

    @Test
    fun aNewInboundSendsThePanelDefaultsAndAnEmptyTagToGenerate() {
        val body = sharedJson.encodeToJsonElement(InboundModel.serializer(), InboundModel()).jsonObject
        assertEquals("", body.getValue("tag").jsonPrimitive.content)
        assertEquals("1", body.getValue("subSortIndex").toString())
        assertEquals("false", body.getValue("excludeFromSub").toString())
        assertEquals("1", body.getValue("trafficResetDay").toString())
    }
}
