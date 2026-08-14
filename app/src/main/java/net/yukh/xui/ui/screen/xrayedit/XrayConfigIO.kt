package net.yukh.xui.ui.screen.xrayedit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.data.json.asObject
import net.yukh.xui.data.json.string
import net.yukh.xui.data.repo.PanelRepository

/**
 * Shared load/save plumbing for the structured Xray-config section editors
 * (General, DNS, Routing). Each edits a slice of the one config JsonObject and
 * round-trips the whole thing, preserving sibling/unknown keys. Session-gated
 * like the raw Xray config screen.
 */
val xrayPrettyJson = Json { prettyPrint = true; isLenient = true }

/**
 * Loaded config + the (separate) outbound test URL from the envelope, plus the
 * tags of outbounds injected from outbound subscriptions. Those aren't in the
 * config template, but routing rules and balancers can target them, so pickers
 * must offer them alongside the manual outbounds.
 */
data class XrayConfigLoad(
    val config: JsonObject,
    val testUrl: String,
    val subscriptionOutboundTags: List<String> = emptyList(),
)

suspend fun PanelRepository.loadXrayConfig(): Result<XrayConfigLoad> =
    getXraySetting().map { env ->
        XrayConfigLoad(
            config = env.xraySetting.asObject(),
            testUrl = env.outboundTestUrl,
            subscriptionOutboundTags = env.subscriptionOutbounds
                .map { it.string("tag") }
                .filter { it.isNotBlank() },
        )
    }

suspend fun PanelRepository.saveXrayConfig(config: JsonObject, testUrl: String): Result<Unit> =
    updateXraySetting(xrayPrettyJson.encodeToString(JsonElement.serializer(), config), testUrl.trim())
