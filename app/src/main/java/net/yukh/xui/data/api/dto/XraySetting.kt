package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Inner payload of POST /panel/xray/. The endpoint wraps this as a JSON
 * STRING in the response `obj`, so it's parsed in two steps: obj (String) →
 * this. `xraySetting` is the full Xray config object (outbounds, routing,
 * dns, …) — the same thing the web panel's Xray Configuration page edits.
 */
@Serializable
data class XraySettingEnvelope(
    val xraySetting: JsonElement = kotlinx.serialization.json.JsonObject(emptyMap()),
    val outboundTestUrl: String = "https://www.google.com/generate_204",
)
