package net.yukh.xui.shared.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Xray observatories as the panel 3.10 manages them (frontend balancer-helpers.ts,
// ObservatorySettingsTab.tsx): created, re-scoped and dropped from the balancers.

private const val OBSERVATORY = "observatory"
private const val BURST = "burstObservatory"
private const val PING = "pingConfig"

private val DEFAULT_OBSERVATORY = JsonObject(
    linkedMapOf(
        "subjectSelector" to JsonArray(emptyList()),
        "probeURL" to JsonPrimitive("https://www.google.com/generate_204"),
        "probeInterval" to JsonPrimitive("1m"),
        "enableConcurrency" to JsonPrimitive(true),
    ),
)

private val DEFAULT_PING_CONFIG = JsonObject(
    linkedMapOf(
        "destination" to JsonPrimitive("https://www.google.com/generate_204"),
        "interval" to JsonPrimitive("1m"),
        "connectivity" to JsonPrimitive(""),
        "timeout" to JsonPrimitive("5s"),
        "sampling" to JsonPrimitive(2),
        "httpMethod" to JsonPrimitive("HEAD"),
    ),
)

private val DEFAULT_BURST_OBSERVATORY = JsonObject(
    linkedMapOf("subjectSelector" to JsonArray(emptyList()), PING to DEFAULT_PING_CONFIG),
)

private fun parseConfig(config: String): JsonObject? =
    runCatching { parseJson.parseToJsonElement(config.ifBlank { "{}" }) as? JsonObject }.getOrNull()

private fun JsonElement?.asObj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

private fun JsonObject.balancers(): List<JsonObject> =
    (this["routing"].asObj()["balancers"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

private fun JsonObject.strategyType(): String = this["strategy"].asObj().str("type")

private fun JsonObject.with(key: String, value: JsonElement?): JsonObject =
    JsonObject(LinkedHashMap(this).also { if (value == null) it.remove(key) else it[key] = value })

/** Present like the panel's `raw == null` check: a JSON null counts as absent. */
private fun JsonObject.has(key: String): Boolean = this[key].let { it != null && it !is JsonNull }

private fun mergeOver(base: JsonObject, vararg layers: JsonObject): JsonObject =
    JsonObject(LinkedHashMap<String, JsonElement>(base).also { m -> layers.forEach { m.putAll(it) } })

private fun balancerRequiresBurst(b: JsonObject): Boolean {
    val type = b.strategyType().ifBlank { "random" }
    return type == "leastLoad" || ((type == "random" || type == "roundRobin") && b.str("fallbackTag").isNotEmpty())
}

private fun collectSelectors(list: List<JsonObject>): JsonArray {
    val out = LinkedHashSet<String>()
    list.forEach { b -> (b["selector"] as? JsonArray)?.forEach { s -> (s as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }?.let(out::add) } }
    return JsonArray(out.map { JsonPrimitive(it) })
}

private fun JsonObject.withSelectors(key: String, default: JsonObject, selectors: JsonArray): JsonObject {
    val current = this[key] as? JsonObject ?: default
    return with(key, current.with("subjectSelector", selectors))
}

/** leastLoad, or random/roundRobin with a fallbackTag, makes Xray require the burst
 *  observer (issue #5605) — without one the core aborts with unresolved dependencies. */
fun settingsRequireBurstObservatory(config: String): Boolean =
    parseConfig(config)?.balancers()?.any(::balancerRequiresBurst) ?: false

/** The panel's syncObservatories: each observer exists only while a balancer needs it,
 *  watching those balancers' selectors; probe settings already set are kept. */
fun syncObservatories(config: String): String {
    var t = parseConfig(config) ?: return config
    val balancers = t.balancers()
    val leastPings = balancers.filter { it.strategyType() == "leastPing" }
    val required = balancers.filter(::balancerRequiresBurst)
    if (required.isNotEmpty()) {
        t = t.with(OBSERVATORY, null)
            .withSelectors(BURST, DEFAULT_BURST_OBSERVATORY, collectSelectors(required + leastPings))
    } else {
        t = t.with(BURST, null)
        t = if (leastPings.isNotEmpty()) t.withSelectors(OBSERVATORY, DEFAULT_OBSERVATORY, collectSelectors(leastPings))
        else t.with(OBSERVATORY, null)
    }
    return encode(t)
}

/** Which observer the settings show, like the panel tab: "observatory",
 *  "burstObservatory", or null when neither exists. */
fun observatoryActiveView(config: String): String? {
    val t = parseConfig(config) ?: return null
    val hasObs = t.has(OBSERVATORY)
    val hasBurst = t.has(BURST)
    return when {
        hasBurst && (!hasObs || settingsRequireBurstObservatory(config)) -> BURST
        hasObs -> OBSERVATORY
        else -> null
    }
}

/** Both observers present: a legacy mix Xray cannot run with one global observer. */
fun hasMixedObservers(config: String): Boolean =
    parseConfig(config)?.let { it.has(OBSERVATORY) && it.has(BURST) } ?: false

/** `observatory` with the panel defaults filled in, or null if absent. */
fun observatoryWithDefaults(config: String): String? {
    val t = parseConfig(config) ?: return null
    if (!t.has(OBSERVATORY)) return null
    return encode(mergeOver(DEFAULT_OBSERVATORY, t[OBSERVATORY].asObj()))
}

/** `burstObservatory` with the panel defaults filled in (pingConfig too), or null if absent. */
fun burstObservatoryWithDefaults(config: String): String? {
    val t = parseConfig(config) ?: return null
    if (!t.has(BURST)) return null
    return encode(mergedBurst(t[BURST].asObj(), JsonObject(emptyMap())))
}

private fun mergedBurst(current: JsonObject, pingPatch: JsonObject): JsonObject {
    val ping = mergeOver(DEFAULT_PING_CONFIG, current[PING].asObj(), pingPatch)
    return mergeOver(DEFAULT_BURST_OBSERVATORY, current).with(PING, ping)
}

private fun patchObservatory(config: String, key: String, value: JsonPrimitive): String {
    val t = parseConfig(config) ?: return config
    val patch = JsonObject(mapOf(key to value))
    return encode(t.with(OBSERVATORY, mergeOver(DEFAULT_OBSERVATORY, t[OBSERVATORY].asObj(), patch)))
}

private fun patchPingConfig(config: String, key: String, value: JsonPrimitive): String {
    val t = parseConfig(config) ?: return config
    return encode(t.with(BURST, mergedBurst(t[BURST].asObj(), JsonObject(mapOf(key to value)))))
}

/** Set an `observatory` field (probeURL, probeInterval), merging in the defaults. */
fun putObservatoryField(config: String, key: String, value: String): String =
    patchObservatory(config, key, JsonPrimitive(value))

fun putObservatoryConcurrency(config: String, value: Boolean): String =
    patchObservatory(config, "enableConcurrency", JsonPrimitive(value))

/** Set a `burstObservatory.pingConfig` field (destination, connectivity, interval,
 *  timeout, httpMethod), merging in the defaults. */
fun putBurstPingField(config: String, key: String, value: String): String =
    patchPingConfig(config, key, JsonPrimitive(value))

fun putBurstPingSampling(config: String, value: Int): String =
    patchPingConfig(config, "sampling", JsonPrimitive(value))
