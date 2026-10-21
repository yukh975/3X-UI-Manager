package net.yukh.xui.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionProfileEditTest {

    private fun profile(url: String, name: String = "", id: String = "p1", token: String = "t") =
        ConnectionProfile(baseUrl = url, auth = ConnectionAuth.Token(token), id = id, name = name)

    @Test
    fun `legacy host-as-name is not a custom name, so a URL edit relabels the profile`() {
        val legacy = profile("https://old.example:2053/", name = "old.example:2053")
        assertEquals("", legacy.customName)

        val moved = legacy.edited(
            baseUrl = "https://new.example:2053/", allowInsecureTls = false, token = "t",
            subBaseUrl = "", name = legacy.customName,
        )
        assertEquals("new.example:2053", moved.label)
    }

    @Test
    fun `a custom name survives a URL edit and blank falls back to the host`() {
        val named = profile("https://old.example/", name = "Office")
        assertEquals("Office", named.customName)
        assertEquals(
            "Office",
            named.edited("https://new.example/", false, "t", "", named.customName).label,
        )
        assertEquals("old.example", named.edited(named.baseUrl, false, "t", "", "  ").label)
    }

    @Test
    fun `edit keeps the id and replaces connection fields`() {
        val p = profile("https://a.example/", id = "keep-me")
        val e = p.edited("https://b.example/", true, "new-token", "https://b.example/sub/", "B")
        assertEquals("keep-me", e.id)
        assertEquals("https://b.example/", e.baseUrl)
        assertTrue(e.allowInsecureTls)
        assertEquals(ConnectionAuth.Token("new-token"), e.auth)
        assertEquals("https://b.example/sub/", e.subBaseUrl)
    }

    @Test
    fun `only connection changes need the panel re-checked, not a rename`() {
        val p = profile("https://a.example/", name = "A")
        assertFalse(p.edited(p.baseUrl, false, "t", "", "Renamed").connectionDiffersFrom(p))
        assertFalse(p.edited(p.baseUrl, false, "t", "https://a.example/sub/", "A").connectionDiffersFrom(p))
        assertTrue(p.edited("https://b.example/", false, "t", "", "A").connectionDiffersFrom(p))
        assertTrue(p.edited(p.baseUrl, true, "t", "", "A").connectionDiffersFrom(p))
        assertTrue(p.edited(p.baseUrl, false, "t2", "", "A").connectionDiffersFrom(p))
    }

    @Test
    fun `a URL already saved under another profile is a conflict, its own URL is not`() {
        val list = listOf(profile("https://a.example/", id = "a"), profile("https://b.example/", id = "b"))
        assertTrue(list.urlTakenByOther(id = "a", baseUrl = "https://b.example/"))
        assertFalse(list.urlTakenByOther(id = "a", baseUrl = "https://a.example/"))
        assertFalse(list.urlTakenByOther(id = "a", baseUrl = "https://c.example/"))
    }
}
