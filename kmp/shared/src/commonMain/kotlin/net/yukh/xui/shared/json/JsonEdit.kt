package net.yukh.xui.shared.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Path-based JSON object editing for the structured inbound editor, kept in
 * `:shared` because `composeApp` does not depend on kotlinx-serialization. Each
 * function takes a JSON OBJECT string + a key path (the chain of nested object
 * keys ending in the leaf), and returns plain String / Boolean / List<String>
 * (getters) or the re-serialized object string (setters). Setters create missing
 * intermediate objects and leave every other key untouched, so unmodeled config
 * (header types, externalProxy, heartbeatPeriod, …) survives the round-trip —
 * mirroring the Android editor's immutable JsonObject helpers.
 */

private val parseJson = Json { ignoreUnknownKeys = true; isLenient = true }
private val prettyJson = Json {
    ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true
    prettyPrint = true; prettyPrintIndent = "  "
}

private fun parseObj(s: String): JsonObject = try {
    parseJson.parseToJsonElement(s.ifBlank { "{}" }) as? JsonObject ?: JsonObject(emptyMap())
} catch (e: Exception) {
    JsonObject(emptyMap())
}

private fun JsonObject.child(key: String): JsonObject = (this[key] as? JsonObject) ?: JsonObject(emptyMap())

private fun JsonObject.descend(path: List<String>): JsonObject {
    var cur = this
    for (k in path) cur = cur.child(k)
    return cur
}

/** Immutable nested put: returns a copy with [path] set to [value] (null removes). */
private fun JsonObject.withPut(path: List<String>, value: JsonElement?): JsonObject {
    val key = path.first()
    return if (path.size == 1) {
        JsonObject(LinkedHashMap(this).also { if (value == null) it.remove(key) else it[key] = value })
    } else {
        val newChild = child(key).withPut(path.drop(1), value)
        JsonObject(LinkedHashMap(this).also { it[key] = newChild })
    }
}

private fun encode(obj: JsonObject): String = prettyJson.encodeToString(JsonElement.serializer(), obj)

fun jsonGetString(obj: String, path: List<String>): String {
    if (path.isEmpty()) return ""
    val parent = parseObj(obj).descend(path.dropLast(1))
    return (parent[path.last()] as? JsonPrimitive)?.contentOrNull ?: ""
}

fun jsonGetBool(obj: String, path: List<String>): Boolean {
    if (path.isEmpty()) return false
    val parent = parseObj(obj).descend(path.dropLast(1))
    return (parent[path.last()] as? JsonPrimitive)?.booleanOrNull ?: false
}

fun jsonGetStrings(obj: String, path: List<String>): List<String> {
    if (path.isEmpty()) return emptyList()
    val parent = parseObj(obj).descend(path.dropLast(1))
    return (parent[path.last()] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
}

fun jsonPutString(obj: String, path: List<String>, value: String): String =
    encode(parseObj(obj).withPut(path, JsonPrimitive(value)))

fun jsonPutBool(obj: String, path: List<String>, value: Boolean): String =
    encode(parseObj(obj).withPut(path, JsonPrimitive(value)))

fun jsonPutStrings(obj: String, path: List<String>, values: List<String>): String =
    encode(parseObj(obj).withPut(path, JsonArray(values.map { JsonPrimitive(it) })))

/** Toggle [item]'s membership in the string array at [path]. */
fun jsonToggleString(obj: String, path: List<String>, item: String): String {
    val cur = jsonGetStrings(obj, path)
    val next = if (item in cur) cur - item else cur + item
    return jsonPutStrings(obj, path, next)
}

/** Parse a comma/space/newline-separated list into trimmed non-blank items. */
fun parseCsvList(text: String): List<String> =
    text.split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }
