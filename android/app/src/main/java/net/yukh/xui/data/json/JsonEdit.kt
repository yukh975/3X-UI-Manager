package net.yukh.xui.data.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Small immutable helpers for editing kotlinx JsonObjects in place-ish: each
 * `put*` returns a new object with one key changed, leaving every other key
 * untouched. Used by the structured inbound editor so unmodeled config keys
 * (externalProxy, heartbeatPeriod, header types, …) survive a round-trip.
 */

fun JsonElement?.asObject(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

fun JsonObject.put(key: String, value: JsonElement?): JsonObject =
    JsonObject(LinkedHashMap(this).also { if (value == null) it.remove(key) else it[key] = value })

fun JsonObject.putString(key: String, value: String): JsonObject = put(key, JsonPrimitive(value))

fun JsonObject.putBool(key: String, value: Boolean): JsonObject = put(key, JsonPrimitive(value))

fun JsonObject.putStrings(key: String, values: List<String>): JsonObject =
    put(key, JsonArray(values.map { JsonPrimitive(it) }))

fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

fun JsonObject.child(key: String): JsonObject = (this[key] as? JsonObject) ?: JsonObject(emptyMap())

fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

/** Parse a comma/space/newline separated list into trimmed non-blank items. */
fun parseCsv(text: String): List<String> =
    text.split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }
