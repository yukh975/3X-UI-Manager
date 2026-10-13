package net.yukh.xui.shared.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

/** Xray editor JSON rules shared with the Android app (0.15.1, panel 3.10). */
class XrayEditJsonTest {

    private fun same(expected: String, actual: String) =
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual))

    private val settings = """{"expected":2,"baselines":["1s"],"costs":[{"match":"a","value":1}]}"""

    @Test
    fun leastLoadBalancerKeepsItsSettings() {
        val b = """{"tag":"b","strategy":{"type":"leastLoad","settings":$settings}}"""
        same(b, normalizeBalancer(b))
    }

    @Test
    fun otherBalancerStrategiesDropSettingsAndRandomDropsTheStrategy() {
        same(
            """{"tag":"b","strategy":{"type":"leastPing"}}""",
            normalizeBalancer("""{"tag":"b","strategy":{"type":"leastPing","settings":$settings}}"""),
        )
        same(
            """{"tag":"b"}""",
            normalizeBalancer("""{"tag":"b","strategy":{"type":"random","settings":$settings}}"""),
        )
        same("""{"tag":"b"}""", normalizeBalancer("""{"tag":"b"}"""))
    }

    @Test
    fun dnsServerExpectedIpsEditTheKeyTheServerAlreadyUses() {
        val panel = """{"address":"8.8.8.8","expectedIPs":["geoip:ru"]}"""
        same(
            """{"address":"8.8.8.8","expectedIPs":["geoip:cn"]}""",
            jsonPutStrings(panel, listOf(dnsExpectedIpsKey(panel)), listOf("geoip:cn")),
        )
        val legacy = """{"address":"8.8.8.8"}"""
        same(
            """{"address":"8.8.8.8","expectIPs":["geoip:cn"]}""",
            jsonPutStrings(legacy, listOf(dnsExpectedIpsKey(legacy)), listOf("geoip:cn")),
        )
    }
}
