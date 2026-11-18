package net.yukh.xui.ui.screen.paneladmin

import kotlinx.serialization.json.JsonObject
import net.yukh.xui.data.json.bool
import net.yukh.xui.data.json.boolOrNull
import net.yukh.xui.data.json.int
import net.yukh.xui.data.json.parseCsv
import net.yukh.xui.data.json.put
import net.yukh.xui.data.json.putBool
import net.yukh.xui.data.json.putInt
import net.yukh.xui.data.json.putString
import net.yukh.xui.data.json.string

internal data class PanelHostsSettings(
    val listen: String = "",
    val domain: String = "",
    val port: String = "2053",
) {
    fun applyTo(raw: JsonObject): JsonObject = raw
        .putString("webListen", listen.trim())
        .putString("webDomain", domain.trim())
        .putInt("webPort", (port.toIntOrNull() ?: 2053).coerceIn(1, 65535))

    companion object {
        fun read(raw: JsonObject) = PanelHostsSettings(
            listen = raw.string("webListen"),
            domain = raw.string("webDomain"),
            port = (raw.int("webPort") ?: 2053).toString(),
        )
    }
}

internal enum class PanelBot(
    val prefix: String,
    val tokenKey: String,
    val hasTokenKey: String,
    val clearTokenKey: String,
) {
    Telegram("tg", "tgBotToken", "hasTgBotToken", "clearTgBotToken"),
    Discord("discord", "discordBotToken", "hasDiscordBotToken", "clearDiscordBotToken");

    val recipientKey: String
        get() = when (this) {
            Telegram -> "tgBotChatId"
            Discord -> "discordChannelId"
        }

    fun withTokenUpdate(raw: JsonObject, replacement: String, clear: Boolean): JsonObject {
        val withoutClear = raw.put(clearTokenKey, null)
        return when {
            clear && raw.boolOrNull(hasTokenKey) != null ->
                withoutClear.putString(tokenKey, "").putBool(clearTokenKey, true)
            replacement.isNotBlank() -> withoutClear.putString(tokenKey, replacement.trim())
            else -> withoutClear
        }
    }

    fun afterSave(submitted: JsonObject): JsonObject {
        val cached = submitted.put(clearTokenKey, null)
        val wasConfigured = submitted.boolOrNull(hasTokenKey) ?: return cached
        val configured = !submitted.bool(clearTokenKey) &&
            (wasConfigured || submitted.string(tokenKey).isNotBlank())
        return cached.putString(tokenKey, "").putBool(hasTokenKey, configured)
    }
}

internal data class PanelBotSettings(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val tokenConfigured: Boolean = false,
    val canClearToken: Boolean = false,
    val recipient: String = "",
    val apiServer: String = "",
    val proxy: String = "",
    val adminIds: String = "",
    val language: String = "en-US",
    val schedule: String = "@daily",
    val backup: Boolean = false,
    val cpu: String = "80",
    val memory: String = "80",
    val events: String = "login.attempt,cpu.high",
) {
    fun applyTo(raw: JsonObject, bot: PanelBot): JsonObject {
        val prefix = bot.prefix
        val updated = raw
            .putBool("${prefix}BotEnable", enabled)
            .putString(bot.recipientKey, recipient.trim())
            .putString("${prefix}Lang", language.trim())
            .putString("${prefix}RunTime", schedule.trim())
            .putBool("${prefix}BotBackup", backup)
            .putInt("${prefix}Cpu", (cpu.toIntOrNull() ?: 80).coerceIn(0, 100))
            .putInt("${prefix}Memory", (memory.toIntOrNull() ?: 80).coerceIn(0, 100))
            .putString("${prefix}EnabledEvents", parseCsv(events).joinToString(","))
        return when (bot) {
            PanelBot.Telegram -> updated
                .putString("tgBotAPIServer", apiServer.trim())
                .putString("tgBotProxy", proxy.trim())
            PanelBot.Discord -> updated.putString("discordAdminIds", adminIds.trim())
        }
    }

    companion object {
        fun read(raw: JsonObject, bot: PanelBot): PanelBotSettings {
            val prefix = bot.prefix
            val hasToken = raw.boolOrNull(bot.hasTokenKey)
            return PanelBotSettings(
                supported = raw.boolOrNull("${prefix}BotEnable") != null,
                enabled = raw.bool("${prefix}BotEnable"),
                tokenConfigured = hasToken ?: raw.string(bot.tokenKey).isNotBlank(),
                canClearToken = hasToken != null,
                recipient = raw.string(bot.recipientKey),
                apiServer = raw.string("tgBotAPIServer"),
                proxy = raw.string("tgBotProxy"),
                adminIds = raw.string("discordAdminIds"),
                language = raw.string("${prefix}Lang").ifBlank { "en-US" },
                schedule = raw.string("${prefix}RunTime").ifBlank { "@daily" },
                backup = raw.bool("${prefix}BotBackup"),
                cpu = (raw.int("${prefix}Cpu") ?: 80).toString(),
                memory = (raw.int("${prefix}Memory") ?: 80).toString(),
                events = if ("${prefix}EnabledEvents" in raw) raw.string("${prefix}EnabledEvents")
                    else "login.attempt,cpu.high",
            )
        }
    }
}
