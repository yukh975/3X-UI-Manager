package net.yukh.xui.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogNotesTest {

    private val changelog = """
        # Changelog

        Intro text that belongs to no version.

        ## [Unreleased]

        ### Added
        - Not out yet.

        ## [0.15.10] — 2026-10-20

        ### Fixed
        - Ten.

        ## [0.15.9] — 2026-10-19

        ### Fixed
        - Nine.

        ## [0.15.8] — 2026-10-18

        ### Added
        - Eight.

        ## [0.15.7] — 2026-10-17

        ### Fixed
        - Seven.

        ## [0.15.6] — 2026-10-16

        ### Fixed
        - Six.

        ## [0.14.3] — 2026-09-15

        ### Fixed
        - Old.
    """.trimIndent()

    private val sections = ChangelogNotes.parse(changelog)

    private fun versions(installed: String, latest: String) =
        ChangelogNotes.select(sections, installed, latest).map { it.version }

    @Test
    fun `parse keeps every versioned section with its date and subsections`() {
        assertEquals(
            listOf("0.15.10", "0.15.9", "0.15.8", "0.15.7", "0.15.6", "0.14.3"),
            sections.map { it.version },
        )
        val eight = sections.first { it.version == "0.15.8" }
        assertEquals("2026-10-18", eight.date)
        assertEquals("### Added\n- Eight.", eight.body)
    }

    @Test
    fun `skipped versions are all shown, newest first, installed and older excluded`() {
        assertEquals(listOf("0.15.8", "0.15.7"), versions("0.15.6", "0.15.8"))
        assertEquals(
            listOf("2026-10-18", "2026-10-17"),
            ChangelogNotes.select(sections, "0.15.6", "0.15.8").map { it.date },
        )
    }

    @Test
    fun `from the previous version only the latest is shown`() {
        assertEquals(listOf("0.15.8"), versions("0.15.7", "0.15.8"))
    }

    @Test
    fun `debug suffix on the installed version is ignored`() {
        assertEquals(listOf("0.15.8"), versions("0.15.7-debug", "0.15.8"))
    }

    @Test
    fun `versions compare numerically, not as strings`() {
        assertEquals(listOf("0.15.10"), versions("0.15.9", "0.15.10"))
        assertEquals(listOf("0.15.10", "0.15.9", "0.15.8"), versions("0.15.7", "0.15.10"))
    }

    @Test
    fun `same-day builds are told apart by the build number`() {
        val dated = ChangelogNotes.parse(
            """
            ## [26.10.21.79] — 2026-10-21
            - c
            ## [26.10.21.78] — 2026-10-21
            - b
            ## [26.10.21.77] — 2026-10-21
            - a
            ## [0.15.6] — 2026-10-21
            - old
            """.trimIndent(),
        )
        assertEquals(
            listOf("26.10.21.79", "26.10.21.78"),
            ChangelogNotes.select(dated, "26.10.21 build 77", "26.10.21.79").map { it.version },
        )
    }

    @Test
    fun `versions of the build-suffix era are shown dotted like the current tags`() {
        assertEquals("26.10.21.78", ChangelogNotes.displayVersion("26.10.21b78"))
        assertEquals("26.10.21.77", ChangelogNotes.displayVersion("26.10.21 build 77"))
        assertEquals("26.10.22.79", ChangelogNotes.displayVersion("26.10.22.79"))
        assertEquals("0.15.6", ChangelogNotes.displayVersion("0.15.6"))
    }

    @Test
    fun `unparsable installed version gets the last three`() {
        assertEquals(listOf("0.15.10", "0.15.9", "0.15.8"), versions("garbage", "0.15.10"))
        assertEquals(listOf("0.15.8", "0.15.7", "0.15.6"), versions("", "0.15.8"))
    }

    @Test
    fun `installed not older than latest gets only the latest`() {
        assertEquals(listOf("0.15.8"), versions("0.15.8", "0.15.8"))
        assertEquals(listOf("0.15.8"), versions("0.16.1", "0.15.8"))
    }

    @Test
    fun `latest missing from the changelog falls back to the release body`() {
        assertEquals(emptyList<String>(), versions("0.15.6", "0.15.11"))
    }

    @Test
    fun `each version and the total are capped, oldest dropped first`() {
        val big = List(5) { VersionNotes("0.1.$it", null, "x".repeat(3000)) }
        val capped = ChangelogNotes.cap(big, perVersion = 2000, total = 5000)
        assertEquals(listOf("0.1.0", "0.1.1", "0.1.2"), capped.map { it.version })
        assertEquals(listOf(2000, 2000, 1000), capped.map { it.body.length })
        assertTrue(capped.all { it.body.endsWith("…") })
        val lines = VersionNotes("1", null, List(10) { "- item $it" }.joinToString("\n"))
        assertEquals("- item 0\n- item 1\n- item 2\n- item 3…", ChangelogNotes.cap(listOf(lines), perVersion = 40)[0].body)
        assertEquals("short", ChangelogNotes.cap(listOf(VersionNotes("1", null, "short")))[0].body)
    }
}
