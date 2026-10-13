package net.yukh.xui.shared.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/** Observatory sync + settings, mirroring the panel 3.10 balancer-helpers.ts. */
class ObservatoryTest {

    private fun same(expected: String, actual: String?) =
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual ?: "null"))

    private fun config(balancers: String, extra: String = "") =
        """{"outbounds":[],"routing":{"rules":[],"balancers":[$balancers]}$extra}"""

    private val defaultBurst = """{"subjectSelector":[],"pingConfig":{"destination":"https://www.google.com/generate_204",
        "interval":"1m","connectivity":"","timeout":"5s","sampling":2,"httpMethod":"HEAD"}}"""
    private val defaultObs = """{"subjectSelector":[],"probeURL":"https://www.google.com/generate_204",
        "probeInterval":"1m","enableConcurrency":true}"""

    private fun child(json: String, key: String) = jsonGetChild(json, listOf(key))

    @Test
    fun leastLoadGetsBurstObserverWatchingLeastPingSelectorsToo() {
        val cfg = config(
            """{"tag":"ll","selector":["a","b"],"strategy":{"type":"leastLoad"}},
               {"tag":"lp","selector":["b","c",""],"strategy":{"type":"leastPing"}},
               {"tag":"r","selector":["d"]}""",
            ""","observatory":$defaultObs""",
        )
        val out = syncObservatories(cfg)
        assertEquals("null", child(out, "observatory"))
        same(defaultBurst.replace("\"subjectSelector\":[]", "\"subjectSelector\":[\"a\",\"b\",\"c\"]"), child(out, "burstObservatory"))
    }

    @Test
    fun randomAndRoundRobinNeedBurstOnlyWithAFallbackTag() {
        for (strategy in listOf("", ""","strategy":{"type":"roundRobin"}""")) {
            val withFallback = syncObservatories(config("""{"tag":"r","selector":["x"],"fallbackTag":"direct"$strategy}"""))
            same(defaultBurst.replace("[]", "[\"x\"]"), child(withFallback, "burstObservatory"))
            val noFallback = syncObservatories(config("""{"tag":"r","selector":["x"],"fallbackTag":""$strategy}"""))
            assertEquals("null", child(noFallback, "burstObservatory"))
            assertEquals("null", child(noFallback, "observatory"))
        }
    }

    @Test
    fun leastPingAloneGetsThePlainObservatory() {
        val cfg = config("""{"tag":"lp","selector":["a"],"strategy":{"type":"leastPing"}}""", ""","burstObservatory":$defaultBurst""")
        val out = syncObservatories(cfg)
        assertEquals("null", child(out, "burstObservatory"))
        same(defaultObs.replace("[]", "[\"a\"]"), child(out, "observatory"))
    }

    @Test
    fun noObserverBackedBalancerRemovesBothObservers() {
        val cfg = config("""{"tag":"r","selector":["a"]}""", ""","observatory":$defaultObs,"burstObservatory":$defaultBurst""")
        val out = syncObservatories(cfg)
        assertEquals("null", child(out, "observatory"))
        assertEquals("null", child(out, "burstObservatory"))
        same("""[{"tag":"r","selector":["a"]}]""", jsonGetChild(out, listOf("routing", "balancers")))
    }

    @Test
    fun existingObserverSettingsSurviveWhileSelectorsAreRewritten() {
        val burst = """{"subjectSelector":["old"],"pingConfig":{"destination":"https://x/204","sampling":5},"custom":1}"""
        val out = syncObservatories(config("""{"tag":"ll","selector":["new"],"strategy":{"type":"leastLoad"}}""", ""","burstObservatory":$burst"""))
        same("""{"subjectSelector":["new"],"pingConfig":{"destination":"https://x/204","sampling":5},"custom":1}""", child(out, "burstObservatory"))

        val obs = """{"subjectSelector":["old"],"probeInterval":"30s"}"""
        val out2 = syncObservatories(config("""{"tag":"lp","selector":["n"],"strategy":{"type":"leastPing"}}""", ""","observatory":$obs"""))
        same("""{"subjectSelector":["n"],"probeInterval":"30s"}""", child(out2, "observatory"))
    }

    @Test
    fun unparsableConfigIsLeftAlone() {
        assertEquals("{not json", syncObservatories("{not json"))
    }

    @Test
    fun activeViewAndMixedObserversFollowThePanelTab() {
        val lp = """{"tag":"lp","selector":["a"],"strategy":{"type":"leastPing"}}"""
        val ll = """{"tag":"ll","selector":["a"],"strategy":{"type":"leastLoad"}}"""
        val both = ""","observatory":$defaultObs,"burstObservatory":$defaultBurst"""
        assertNull(observatoryActiveView(config(lp)))
        assertEquals("observatory", observatoryActiveView(config(lp, ""","observatory":$defaultObs""")))
        assertEquals("burstObservatory", observatoryActiveView(config(lp, ""","burstObservatory":$defaultBurst""")))
        assertEquals("observatory", observatoryActiveView(config(lp, both)))
        assertEquals("burstObservatory", observatoryActiveView(config(ll, both)))
        assertTrue(hasMixedObservers(config(lp, both)))
        assertFalse(hasMixedObservers(config(lp, ""","observatory":$defaultObs,"burstObservatory":null""")))
    }

    @Test
    fun editsMergeThePanelDefaults() {
        val cfg = config("", ""","observatory":{"subjectSelector":["a"]},"burstObservatory":{"pingConfig":{"timeout":"9s"}}""")
        same(
            defaultObs.replace("[]", "[\"a\"]").replace("\"1m\"", "\"30s\""),
            child(putObservatoryField(cfg, "probeInterval", "30s"), "observatory"),
        )
        same(
            defaultBurst.replace("\"5s\"", "\"9s\"").replace("\"sampling\":2", "\"sampling\":4"),
            child(putBurstPingSampling(cfg, 4), "burstObservatory"),
        )
        same(defaultBurst.replace("\"5s\"", "\"9s\""), burstObservatoryWithDefaults(cfg))
        assertEquals("false", jsonGetChild(putObservatoryConcurrency(cfg, false), listOf("observatory", "enableConcurrency")))
        assertNull(observatoryWithDefaults(config("")))
    }
}
