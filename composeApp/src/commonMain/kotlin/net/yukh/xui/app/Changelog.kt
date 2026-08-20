package net.yukh.xui.app

/**
 * One released version as shown on the in-app changelog page: its number, its
 * date, and the "Added"/"Fixed"/… groups underneath.
 */
data class ChangelogRelease(
    val version: String,
    val date: String,
    val groups: List<ChangelogGroup>,
)

data class ChangelogGroup(val title: String, val items: List<String>)

/**
 * The app's own CHANGELOG, compiled into the binary at build time (see the
 * `generateChangelogSource` task in composeApp/build.gradle.kts) rather than
 * fetched: the page has to work offline and must never contact our
 * infrastructure. It therefore documents exactly the installed version and
 * everything before it — which is what a history page should show.
 */
object Changelog {

    /** Parsed releases, newest first. */
    fun load(lang: String): List<ChangelogRelease> =
        parse(if (lang == LANG_RU) ChangelogSource.ru else ChangelogSource.en)

    /**
     * Parse the Keep-a-Changelog markdown we author: `## [1.2.3] — 2026-01-01`
     * headings, `### Added` groups, `- ` bullets. The file's own preamble (title,
     * format note, language switch link) sits before the first `## [` and is
     * skipped. Inline `**bold**` is left in place for the renderer to style.
     */
    internal fun parse(md: String): List<ChangelogRelease> {
        val releases = mutableListOf<ChangelogRelease>()
        var version: String? = null
        var date = ""
        var groups = mutableListOf<ChangelogGroup>()
        var groupTitle: String? = null
        var items = mutableListOf<String>()

        fun flushGroup() {
            val t = groupTitle
            if (t != null && items.isNotEmpty()) groups.add(ChangelogGroup(t, items.toList()))
            groupTitle = null
            items = mutableListOf()
        }
        fun flushRelease() {
            flushGroup()
            val v = version
            if (v != null && groups.isNotEmpty()) releases.add(ChangelogRelease(v, date, groups.toList()))
            version = null
            date = ""
            groups = mutableListOf()
        }

        for (raw in md.lineSequence()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("## ") -> {
                    flushRelease()
                    val head = line.removePrefix("## ").trim()
                    val v = Regex("""\[([^\]]+)]""").find(head)?.groupValues?.get(1)
                    if (v != null) {
                        version = v
                        date = head.substringAfter("]").trim().trimStart('—', '-', '–').trim()
                    }
                }
                line.startsWith("### ") -> {
                    if (version != null) {
                        flushGroup()
                        groupTitle = line.removePrefix("### ").trim()
                    }
                }
                line.startsWith("- ") -> {
                    if (version != null) items.add(line.removePrefix("- ").trim())
                }
                line.isBlank() -> Unit
                else -> {
                    if (version != null && items.isNotEmpty()) {
                        items[items.lastIndex] = items.last() + " " + line.trim()
                    }
                }
            }
        }
        flushRelease()
        return releases
    }
}
