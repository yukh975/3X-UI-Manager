package net.yukh.xui.shared.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.yukh.xui.shared.api.sharedJson

/** Fields the panel's client/node update overwrites from the body must come back
 *  unchanged from an app save; plus the panel 3.10 client rules. */
class ClientsNodesSaveTest {

    private fun listedClient(json: String): Client = sharedJson.decodeFromString(Client.serializer(), json)

    private fun body(model: ClientModel): JsonObject =
        sharedJson.encodeToJsonElement(ClientModel.serializer(), model).jsonObject

    private fun body(model: NodeModel): JsonObject =
        sharedJson.encodeToJsonElement(NodeModel.serializer(), model).jsonObject

    @Test
    fun clientSaveKeepsWeeklyRenewalAndReverse() {
        val row = listedClient("""{"email":"a","resetWeekday":3,"reverse":{"tag":"rev-a"}}""")
        val sent = body(row.toModel())
        assertEquals(3, sent.getValue("resetWeekday").jsonPrimitive.int)
        assertEquals("rev-a", sent.getValue("reverse").jsonObject.getValue("tag").jsonPrimitive.content)
    }

    @Test
    fun clientReverseFromAnOlderPanelsJsonTextIsSentAsAnObject() {
        val row = listedClient("""{"email":"a","reverse":"{\"tag\":\"rev-a\"}"}""")
        assertEquals("rev-a", body(row.toModel()).getValue("reverse").jsonObject.getValue("tag").jsonPrimitive.content)
    }

    @Test
    fun clientWithoutReverseLeavesTheKeyOut() {
        for (json in listOf("""{"email":"a"}""", """{"email":"a","reverse":null}""", """{"email":"a","reverse":{"tag":""}}""")) {
            assertFalse("reverse" in body(listedClient(json).toModel()), json)
        }
    }

    @Test
    fun aResetPeriodOrRenewalDayTurnsWeeklyRenewalOff() {
        val weekly = listedClient("""{"email":"a","resetWeekday":5}""").toModel()
        assertEquals(5, weekly.withRenewal(reset = 0, resetDay = 0).resetWeekday)
        assertEquals(0, weekly.withRenewal(reset = 30, resetDay = 0).resetWeekday)
        val byDay = weekly.withRenewal(reset = 0, resetDay = 12)
        assertEquals(0, byDay.resetWeekday)
        assertEquals(12, byDay.resetDay)
    }

    @Test
    fun masqueRefusesAColonInTheEmail() {
        assertTrue(masqueEmailInvalid("user:1", listOf("vless", "masque")))
        assertFalse(masqueEmailInvalid("user:1", listOf("vless", "tuic")))
        assertFalse(masqueEmailInvalid("user-1", listOf("masque")))
    }

    @Test
    fun nodeSaveKeepsSelectedInboundSyncAndPin() {
        val node = sharedJson.decodeFromString(
            Node.serializer(),
            """{"id":4,"name":"n","tlsVerifyMode":"pin","pinnedCertSha256":"ab12","outboundTag":"via",
               "inboundSyncMode":"selected","inboundTags":["in-443-tcp","in-8443-tcp"]}""",
        )
        val sent = body(node.toModel())
        assertEquals("selected", sent.getValue("inboundSyncMode").jsonPrimitive.content)
        assertEquals(listOf("in-443-tcp", "in-8443-tcp"), sent.getValue("inboundTags").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("ab12", sent.getValue("pinnedCertSha256").jsonPrimitive.content)
        assertEquals("via", sent.getValue("outboundTag").jsonPrimitive.content)
    }

    @Test
    fun nodeWithNullInboundTagsStillParses() {
        val node = sharedJson.decodeFromString(Node.serializer(), """{"id":1,"inboundSyncMode":"all","inboundTags":null}""")
        assertEquals(emptyList(), node.inboundTags)
    }

    @Test
    fun jsonSubscriptionUrlOnlyWhileTheJsonEndpointIsOn() {
        fun settings(json: String) = sharedJson.decodeFromString(PanelSubSettings.serializer(), json)
        assertNull(settings("""{"subEnable":true}""").jsonSubscriptionUrl("panel.test", "abc"))
        assertNull(settings("""{"subEnable":false,"subJsonEnable":true}""").jsonSubscriptionUrl("panel.test", "abc"))
        assertEquals(
            "http://panel.test:2096/json/abc",
            settings("""{"subEnable":true,"subJsonEnable":true,"subPort":2096}""").jsonSubscriptionUrl("panel.test", "abc"),
        )
        assertEquals(
            "https://sub.test/cfg/abc",
            settings("""{"subEnable":true,"subJsonEnable":true,"subJsonURI":"https://sub.test/cfg"}""")
                .jsonSubscriptionUrl("panel.test", "abc"),
        )
        // The plain subscription URL is unchanged by the shared builder.
        assertEquals(
            "https://sub.test/s/abc",
            settings("""{"subEnable":true,"subDomain":"sub.test","subPort":443,"subPath":"s","subCertFile":"c","subKeyFile":"k"}""")
                .subscriptionUrl("panel.test", "abc"),
        )
    }

    @Test
    fun postQuantumLinksAreTold() {
        val x25519 = "G3cdPSd1-NnlpTbWNSM5vHsT5VNzWfFzYSKwbUMnV1Y"
        val mlkem = "A".repeat(1579)
        val base = "vless://id@example.test:443?type=tcp&security=reality&pbk=k&fp=chrome"
        assertFalse(isPostQuantumLink("$base#plain"))
        assertFalse(isPostQuantumLink("$base&encryption=mlkem768x25519plus.native.0rtt.$x25519#x"))
        assertTrue(isPostQuantumLink("$base&encryption=mlkem768x25519plus.native.0rtt.$mlkem#pq"))
        assertTrue(isPostQuantumLink("$base&pqv=${"B".repeat(2603)}#pq"))
        assertFalse(isPostQuantumLink("$base&pqv=#empty"))
        // A key in the remark is not a parameter.
        assertFalse(isPostQuantumLink("$base#pqv=${"B".repeat(10)}"))
        assertFalse(isPostQuantumLink("https://sub.test/sub/abc"))
    }
}
