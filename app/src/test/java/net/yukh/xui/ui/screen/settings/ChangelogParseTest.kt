package net.yukh.xui.ui.screen.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The in-app history page is only as good as this parser — it reads the very
 *  CHANGELOG.md we author, so the real file's shape is what's asserted here. */
class ChangelogParseTest {

    private val sample = """
        # Changelog

        All notable changes are documented here.

        🇷🇺 [Версия на русском](CHANGELOG.ru.md)

        ## [0.10.3] — 2026-08-08

        ### Added
        - **Changelog page** in About — browse the full release history.

        ## [0.10.2] — 2026-08-08

        ### Fixed
        - Clients created in the app now get a proper subscription ID.

        ### Added
        - Something else entirely.
    """.trimIndent()

    @Test
    fun `parses releases newest first with dates`() {
        val releases = Changelog.parse(sample)
        assertEquals(listOf("0.10.3", "0.10.2"), releases.map { it.version })
        assertEquals("2026-08-08", releases[0].date)
    }

    @Test
    fun `keeps groups and their bullets`() {
        val releases = Changelog.parse(sample)
        assertEquals(listOf("Added"), releases[0].groups.map { it.title })
        assertEquals(listOf("Fixed", "Added"), releases[1].groups.map { it.title })
        assertEquals(1, releases[1].groups[0].items.size)
        assertTrue(releases[0].groups[0].items[0].startsWith("**Changelog page**"))
    }

    @Test
    fun `skips the preamble above the first release`() {
        val releases = Changelog.parse(sample)
        assertTrue(releases.none { it.version.contains("Changelog") })
        assertTrue(releases.flatMap { it.groups }.flatMap { it.items }.none { it.contains("Версия на русском") })
    }

    @Test
    fun `joins a bullet wrapped across lines`() {
        val wrapped = """
            ## [1.0.0] — 2026-01-01

            ### Fixed
            - A bullet that continues
              on the next line.
        """.trimIndent()
        val items = Changelog.parse(wrapped).single().groups.single().items
        assertEquals(listOf("A bullet that continues on the next line."), items)
    }

    @Test
    fun `returns nothing for a file with no releases`() {
        assertTrue(Changelog.parse("# Changelog\n\nnothing here yet\n").isEmpty())
    }
}
