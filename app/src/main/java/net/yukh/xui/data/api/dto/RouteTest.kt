package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Result of POST /panel/api/xray/routeTest (panel 3.5.0). [matched] is false
 * when no rule matched — traffic then uses the default (first) outbound and
 * [outboundTag] is empty. [groupTags] lists the balancer chain, if any.
 */
@Serializable
data class RouteTestResult(
    val matched: Boolean = false,
    val outboundTag: String = "",
    val groupTags: List<String> = emptyList(),
)
