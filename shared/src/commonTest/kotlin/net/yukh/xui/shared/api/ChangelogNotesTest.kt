package net.yukh.xui.shared.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangelogNotesTest {

    private val changelog = """
        # Changelog

        ## [Unreleased]
        - not shipped yet

        ## [0.15.10] — 2026-10-20

        ### Fixed
        - ten

        ## [0.15.9] — 2026-10-19

        ### Added
        - nine

        ## [0.15.8] — 2026-10-18

        ### Fixed
        - eight

        ## [0.15.7] — 2026-10-17

        ### Added
        - seven
          continued

        ## [0.15.6] — 2026-10-16
        - six
    """.trimIndent()

    private val all = ChangelogNotes.parse(changelog)

    private fun versions(installed: String, latest: String) =
        ChangelogNotes.select(all, installed, latest).versions.map { it.version }

    @Test
    fun parseKeepsEveryVersionedSectionWithDateAndSubsections() {
        assertEquals(listOf("0.15.10", "0.15.9", "0.15.8", "0.15.7", "0.15.6"), all.map { it.version })
        assertEquals("2026-10-17", all[3].date)
        assertEquals("### Added\n- seven\n  continued", all[3].body)
    }

    @Test
    fun skippedVersionsAreAllShownNewestFirstAndNothingOlder() {
        val picked = ChangelogNotes.select(all, "0.15.6", "0.15.8")
        assertEquals(listOf("0.15.8", "0.15.7"), picked.versions.map { it.version })
        assertEquals(listOf("2026-10-18", "2026-10-17"), picked.versions.map { it.date })
        assertFalse(picked.olderOmitted)
    }

    @Test
    fun fromThePreviousVersionOnlyTheLatestIsShown() {
        assertEquals(listOf("0.15.8"), versions("0.15.7", "0.15.8"))
    }

    @Test
    fun installedNotOlderThanLatestShowsOnlyTheLatest() {
        assertEquals(listOf("0.15.8"), versions("0.15.8", "0.15.8"))
        assertEquals(listOf("0.15.8"), versions("0.16.1", "0.15.8"))
    }

    @Test
    fun unparsableInstalledVersionShowsTheLastThree() {
        assertEquals(listOf("0.15.10", "0.15.9", "0.15.8"), versions("dev", "0.15.10"))
        assertEquals(listOf("0.15.10", "0.15.9", "0.15.8"), versions("", "0.15.10"))
    }

    @Test
    fun versionsCompareNumericallyNotAsStrings() {
        assertEquals(listOf("0.15.10", "0.15.9"), versions("0.15.8", "0.15.10"))
        assertEquals(listOf("0.15.9"), versions("0.15.8", "0.15.9"))
    }

    @Test
    fun latestWithoutAChangelogSectionGivesNothingSoTheCallerFallsBack() {
        assertTrue(versions("0.15.6", "0.15.11").isEmpty())
    }

    @Test
    fun aLongVersionIsCutAtALineBreakWithinTheCap() {
        val bullets = (1..300).joinToString("\n") { "- change number $it" }
        val long = ChangelogNotes.parse("## [1.0.1] — 2026-01-02\n$bullets\n## [1.0.0] — 2026-01-01\n- old")
        val body = ChangelogNotes.select(long, "1.0.0", "1.0.1").versions.single().body
        assertTrue(body.length <= ChangelogNotes.MAX_VERSION_CHARS + 2, "length ${body.length}")
        assertTrue(body.endsWith("\n…"))
        assertTrue(body.removeSuffix("\n…").lines().all { it.matches(Regex("- change number \\d+")) })
    }

    @Test
    fun theTotalCapDropsTheOldestVersionsAndSaysSo() {
        val block = (1..120).joinToString("\n") { "- item $it padded out to length" }
        val md = (10 downTo 1).joinToString("\n") { "## [2.0.$it] — 2026-02-$it\n$block" }
        val picked = ChangelogNotes.select(ChangelogNotes.parse(md), "2.0.0", "2.0.10")
        assertTrue(picked.versions.sumOf { it.body.length } <= ChangelogNotes.MAX_TOTAL_CHARS)
        assertEquals("2.0.10", picked.versions.first().version)
        assertTrue(picked.versions.size in 2..9, "kept ${picked.versions.size}")
        assertTrue(picked.olderOmitted)
    }
}
