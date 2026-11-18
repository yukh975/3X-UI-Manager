package net.yukh.xui.ui.screen.paneladmin

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.yukh.xui.data.json.bool
import net.yukh.xui.data.json.int
import net.yukh.xui.data.json.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The panel's setting/update overwrites every field, so a save must resend the
 * whole object with only the edited keys changed. Bot tokens arrive redacted
 * (blank) with has* presence flags: a blank token means "unchanged", and only
 * an explicit clear* flag wipes it. These tests pin that contract so a save can
 * never silently erase a configured bot token or a sibling setting.
 */
class PanelAdminSettingsTest {

    private val raw = buildJsonObject {
        put("webListen", "127.0.0.1")
        put("webDomain", "panel.example")
        put("webPort", 2053)
        put("subAnnounce", "keep me")
        put("tgBotEnable", true)
        put("tgBotToken", "")
        put("hasTgBotToken", true)
        put("tgBotChatId", "111")
        put("tgLang", "en-US")
        put("tgRunTime", "@daily")
        put("tgBotBackup", false)
        put("tgCpu", 80)
        put("tgMemory", 80)
        put("tgEnabledEvents", "login.attempt,cpu.high")
        put("discordBotEnable", false)
        put("discordBotToken", "")
        put("hasDiscordBotToken", false)
    }

    // ---- Hosts ----

    @Test
    fun hostsApplyCoercesPortTrimsAndKeepsSiblings() {
        val out = PanelHostsSettings(listen = " 0.0.0.0 ", domain = " x.example ", port = "99999").applyTo(raw)

        assertEquals("0.0.0.0", out.string("webListen"))
        assertEquals("x.example", out.string("webDomain"))
        assertEquals(65535, out.int("webPort"))
        assertEquals("keep me", out.string("subAnnounce"))
    }

    @Test
    fun hostsReadRoundTrips() {
        val h = PanelHostsSettings.read(raw)

        assertEquals("127.0.0.1", h.listen)
        assertEquals("panel.example", h.domain)
        assertEquals("2053", h.port)
    }

    @Test
    fun hostsPortFallsBackWhenBlank() {
        assertEquals(2053, PanelHostsSettings(port = "").applyTo(raw).int("webPort"))
    }

    // ---- Bot token secret contract ----

    @Test
    fun blankTokenWithoutClearLeavesItUnchanged() {
        val out = PanelBot.Telegram.withTokenUpdate(raw, replacement = "", clear = false)

        assertEquals("", out.string("tgBotToken"))
        assertNull(out["clearTgBotToken"])
        assertEquals("keep me", out.string("subAnnounce"))
    }

    @Test
    fun newTokenIsWrittenTrimmed() {
        val out = PanelBot.Telegram.withTokenUpdate(raw, replacement = " 123:ABC ", clear = false)

        assertEquals("123:ABC", out.string("tgBotToken"))
        assertNull(out["clearTgBotToken"])
    }

    @Test
    fun clearSetsFlagWhenPanelSupportsIt() {
        val out = PanelBot.Telegram.withTokenUpdate(raw, replacement = "", clear = true)

        assertEquals("", out.string("tgBotToken"))
        assertTrue(out.bool("clearTgBotToken"))
    }

    @Test
    fun clearIgnoredOnPanelWithoutHasFlag() {
        val old = buildJsonObject {
            put("tgBotEnable", true)
            put("tgBotToken", "")
        }

        val out = PanelBot.Telegram.withTokenUpdate(old, replacement = "", clear = true)

        assertNull(out["clearTgBotToken"])
    }

    @Test
    fun afterSaveRemasksTokenAndMarksConfigured() {
        val submitted = PanelBot.Telegram.withTokenUpdate(raw, replacement = "123:ABC", clear = false)
        val cached = PanelBot.Telegram.afterSave(submitted)

        assertEquals("", cached.string("tgBotToken"))
        assertTrue(cached.bool("hasTgBotToken"))
        assertNull(cached["clearTgBotToken"])
    }

    @Test
    fun afterSaveClearMarksUnconfigured() {
        val submitted = PanelBot.Telegram.withTokenUpdate(raw, replacement = "", clear = true)
        val cached = PanelBot.Telegram.afterSave(submitted)

        assertFalse(cached.bool("hasTgBotToken"))
        assertNull(cached["clearTgBotToken"])
    }

    // ---- Bot field mapping ----

    @Test
    fun telegramApplyMapsKeysCoercesAndKeepsSiblings() {
        val s = PanelBotSettings.read(raw, PanelBot.Telegram).copy(
            enabled = false, recipient = "222", cpu = "150", events = " login.attempt , cpu.high ",
        )
        val out = s.applyTo(raw, PanelBot.Telegram)

        assertFalse(out.bool("tgBotEnable"))
        assertEquals("222", out.string("tgBotChatId"))
        assertEquals(100, out.int("tgCpu"))
        assertEquals("login.attempt,cpu.high", out.string("tgEnabledEvents"))
        assertEquals("keep me", out.string("subAnnounce"))
        assertEquals("", out.string("tgBotToken"))
    }

    @Test
    fun discordReadAndApplyUseChannelAndAdminIds() {
        val d = buildJsonObject {
            put("discordBotEnable", true)
            put("discordChannelId", "999")
            put("discordAdminIds", "1,2")
            put("hasDiscordBotToken", true)
        }
        val s = PanelBotSettings.read(d, PanelBot.Discord)

        assertTrue(s.supported)
        assertTrue(s.tokenConfigured)
        assertEquals("999", s.recipient)
        assertEquals("1,2", s.adminIds)

        val out = s.applyTo(d, PanelBot.Discord)
        assertEquals("999", out.string("discordChannelId"))
        assertEquals("1,2", out.string("discordAdminIds"))
    }

    @Test
    fun unsupportedWhenEnableKeyAbsent() {
        assertFalse(PanelBotSettings.read(buildJsonObject { }, PanelBot.Discord).supported)
    }
}
