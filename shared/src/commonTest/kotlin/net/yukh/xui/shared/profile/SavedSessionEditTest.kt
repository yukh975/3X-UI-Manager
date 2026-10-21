package net.yukh.xui.shared.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Editing a saved panel (#11): the id must outlive a URL change, renames skip the probe. */
class SavedSessionEditTest {

    private fun profile(url: String, name: String = "", id: String = "p1", token: String = "t") =
        SavedSession(baseUrl = url, token = token, name = name, id = id)

    @Test
    fun editKeepsTheIdSoMonitoredInboundsAndAlertsStayAttached() {
        val p = profile("https://a.example/", id = "keep-me")
        val e = p.edited(baseUrl = "https://b.example/", token = "new-token", allowInsecure = true, name = "B")
        assertEquals("keep-me", e.id)
        assertEquals("https://b.example/", e.baseUrl)
        assertEquals("new-token", e.token)
        assertTrue(e.allowInsecure)
        assertEquals("B", e.name)
    }

    @Test
    fun aCustomNameSurvivesAUrlEditAndABlankNameFallsBackToTheHost() {
        val named = profile("https://old.example/", name = "Office")
        assertEquals("Office", named.edited("https://new.example/", "t", false, named.name).label)
        val cleared = named.edited(named.baseUrl, "t", false, "  ")
        assertEquals("", cleared.name)
        assertEquals("old.example", cleared.label)
        assertEquals("Office", named.edited(named.baseUrl, "t", false, "  Office ").name)
    }

    @Test
    fun onlyConnectionChangesNeedThePanelRecheckedNotARename() {
        val p = profile("https://a.example/", name = "A")
        assertFalse(p.edited(p.baseUrl, "t", false, "Renamed").connectionDiffersFrom(p))
        assertTrue(p.edited("https://b.example/", "t", false, "A").connectionDiffersFrom(p))
        assertTrue(p.edited(p.baseUrl, "t", true, "A").connectionDiffersFrom(p))
        assertTrue(p.edited(p.baseUrl, "t2", false, "A").connectionDiffersFrom(p))
    }

    @Test
    fun aUrlAlreadySavedUnderAnotherProfileIsAConflictItsOwnUrlIsNot() {
        val list = listOf(profile("https://a.example/", id = "a"), profile("https://b.example/", id = "b"))
        assertTrue(list.urlTakenByOther(id = "a", baseUrl = "https://b.example/"))
        assertFalse(list.urlTakenByOther(id = "a", baseUrl = "https://a.example/"))
        assertFalse(list.urlTakenByOther(id = "a", baseUrl = "https://c.example/"))
    }

    @Test
    fun theIdSurvivesSaveAndLoadAndRowsSavedBeforeIdsExistedKeepTheUrlAsId() {
        val moved = profile("https://b.example/", name = "B", id = "https://a.example/")
        assertEquals(listOf(moved), decodeSessions(encodeSessions(listOf(moved))))

        val legacyRow = "Old\u0001https://old.example/\u0001tok\u0001true"
        val legacy = decodeSessions(legacyRow).single()
        assertEquals("https://old.example/", legacy.id)
        assertEquals(SavedSession("https://old.example/", "tok", true, "Old"), legacy)
    }

    @Test
    fun signingInAgainToASavedUrlReusesItsIdInsteadOfAddingADuplicate() {
        val saved = listOf(profile("https://a.example/", id = "p1"))
        assertEquals("p1", saved.idFor("https://a.example/"))
        assertNotEquals("p1", saved.idFor("https://c.example/"))
    }

    @Test
    fun aNewPanelAtAUrlAnotherProfileMovedAwayFromGetsItsOwnId() {
        val movedLegacy = listOf(profile("https://b.example/", id = "https://a.example/"))
        assertNotEquals("https://a.example/", movedLegacy.idFor("https://a.example/"))
    }
}
