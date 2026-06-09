package net.yukh.xui.shared.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Inner payload of POST /panel/api/xray/. The endpoint wraps this as a JSON
 * STRING in the response `obj`, so parse in two steps: obj (String) → this.
 * `xraySetting` is the full Xray config (outbounds, routing, dns, …).
 */
@Serializable
data class XraySettingEnvelope(
    val xraySetting: JsonElement = JsonObject(emptyMap()),
    val outboundTestUrl: String = "https://www.google.com/generate_204",
)

/** Editor-ready text form of the Xray config. */
data class XrayConfigText(val configJson: String, val testUrl: String)

private val xrayLax = Json { isLenient = true; ignoreUnknownKeys = true }
private val xrayPretty = Json { prettyPrint = true; prettyPrintIndent = "  "; isLenient = true; ignoreUnknownKeys = true }

/** Decode the double-wrapped getXraySetting obj into pretty config text + test URL. */
fun parseXrayObj(objString: String): XrayConfigText? = try {
    val env = xrayLax.decodeFromString(XraySettingEnvelope.serializer(), objString)
    XrayConfigText(xrayPretty.encodeToString(JsonElement.serializer(), env.xraySetting), env.outboundTestUrl)
} catch (e: Throwable) {
    null
}

/** True if the edited config text is valid JSON (gates the Save button). */
fun isValidJson(text: String): Boolean = try {
    xrayLax.parseToJsonElement(text); true
} catch (e: Throwable) {
    false
}
