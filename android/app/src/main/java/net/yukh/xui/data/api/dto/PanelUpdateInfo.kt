package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/** GET /panel/api/server/getPanelUpdateInfo */
@Serializable
data class PanelUpdateInfo(
    val currentVersion: String = "",
    val latestVersion: String = "",
    val updateAvailable: Boolean = false,
)
