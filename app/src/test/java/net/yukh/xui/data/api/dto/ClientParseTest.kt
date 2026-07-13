package net.yukh.xui.data.api.dto

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the /clients/list parse. The panel serializes a client row from
 * model.ClientRecord, where `allowedIPs` is a comma-separated STRING (the
 * wg_allowed_ips column) — not the array the create/update payload uses.
 * Declaring it as a List once broke the whole list parse (clients screen +
 * dashboard total), so pin the shape here.
 */
class ClientParseTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    @Test
    fun parsesListRowsIncludingWireguardAndMtproto() {
        val payload = """[
          {"id":1,"email":"vless","uuid":"u","enable":true,"inboundIds":[1],"traffic":{"up":5,"down":6,"total":0}},
          {"id":2,"email":"wg","allowedIPs":"10.0.0.2/32, 10.0.0.3/32","keepAlive":25,"publicKey":"pk","privateKey":"sk"},
          {"id":3,"email":"mtproto","secret":"ee00","adTag":"0123456789abcdef0123456789abcdef"}
        ]"""

        val list = json.decodeFromString(ListSerializer(Client.serializer()), payload)

        assertEquals(3, list.size)
        assertEquals(5, list[0].up)
        assertEquals("10.0.0.2/32, 10.0.0.3/32", list[1].allowedIPs)
        // toModel splits the CSV back into the array the write payload expects.
        assertEquals(listOf("10.0.0.2/32", "10.0.0.3/32"), list[1].toModel().allowedIPs)
        assertEquals(25, list[1].keepAlive)
        assertEquals("ee00", list[2].secret)
    }
}
