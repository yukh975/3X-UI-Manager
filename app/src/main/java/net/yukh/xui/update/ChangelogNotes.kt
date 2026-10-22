package net.yukh.xui.update

/** One `## [YY.M.D.N] — YYYY-MM-DD` section of the changelog. */
data class VersionNotes(
    val version: String,
    val date: String?,
    val body: String,
)

/**
 * Picks the changelog sections the update dialog shows: every version newer than
 * the installed one up to the latest, newest first. Pure, so it is unit-tested.
 */
object ChangelogNotes {
    const val PER_VERSION_CAP = 4000
    const val TOTAL_CAP = 8000
    const val FALLBACK_COUNT = 3

    private val HEADING = Regex("""^## \[([^\]]+)\]\s*(?:[—–-]\s*(.*))?$""")
    // b77 and b78 shipped as "26.10.21 build 77" with tag "v26.10.21b77" before the dotted form.
    private val VERSION = Regex("""^v?(\d+(?:\.\d+)*)(?:(?:b| build )(\d+))?""")
    private val SUFFIXED_BUILD = Regex("""^(\d+\.\d+\.\d+)(?:b| build )(\d+)$""")

    /** All versioned sections in file order; `[Unreleased]` and the like are skipped. */
    fun parse(changelog: String): List<VersionNotes> {
        val out = mutableListOf<VersionNotes>()
        var head: MatchResult? = null
        val body = mutableListOf<String>()
        fun flush() {
            val h = head ?: return
            val version = h.groupValues[1].trim()
            if (parseVersion(version) != null) {
                out += VersionNotes(
                    version = version,
                    date = h.groupValues[2].trim().ifEmpty { null },
                    body = body.joinToString("\n").trim(),
                )
            }
        }
        for (line in changelog.lines()) {
            val m = HEADING.find(line.trimEnd())
            if (m != null) {
                flush()
                head = m
                body.clear()
            } else if (line.startsWith("## ")) {
                flush()
                head = null
                body.clear()
            } else if (head != null) {
                body += line
            }
        }
        flush()
        return out
    }

    /**
     * Sections with installed < version ≤ latest, newest first; empty if [latest] has no section.
     * An unparsable [installed] gets the last [FALLBACK_COUNT]; one not older than [latest] only the latest.
     */
    fun select(sections: List<VersionNotes>, installed: String, latest: String): List<VersionNotes> {
        val top = parseVersion(latest) ?: return emptyList()
        val upToLatest = sections
            .mapNotNull { s -> parseVersion(s.version)?.let { it to s } }
            .filter { (v, _) -> compare(v, top) <= 0 }
            .sortedWith { a, b -> compare(b.first, a.first) }
        if (upToLatest.firstOrNull()?.first?.let { compare(it, top) } != 0) return emptyList()
        val from = parseVersion(installed) ?: return upToLatest.take(FALLBACK_COUNT).map { it.second }
        if (compare(from, top) >= 0) return upToLatest.take(1).map { it.second }
        return upToLatest.filter { (v, _) -> compare(v, from) > 0 }.map { it.second }
    }

    /** Trims each body to [perVersion] chars and drops older sections past [total]. */
    fun cap(
        sections: List<VersionNotes>,
        perVersion: Int = PER_VERSION_CAP,
        total: Int = TOTAL_CAP,
    ): List<VersionNotes> {
        val out = mutableListOf<VersionNotes>()
        var used = 0
        for (s in sections) {
            val room = minOf(perVersion, total - used)
            if (room <= 0) break
            val body = if (s.body.length <= room) s.body else truncate(s.body, room)
            out += s.copy(body = body)
            used += body.length
        }
        return out
    }

    /** At most [max] chars ending in "…", cut at a line break when one is in reach. */
    private fun truncate(text: String, max: Int): String {
        val head = text.take(max - 1)
        val lineEnd = head.lastIndexOf('\n')
        return (if (lineEnd > max / 2) head.take(lineEnd) else head).trimEnd() + "…"
    }

    /** "26.10.21.77-debug" and "26.10.21 build 77" → [26, 10, 21, 77], "0.15.2" → [0, 15, 2]; null if none. */
    fun parseVersion(v: String): List<Int>? {
        val m = VERSION.find(v.trim()) ?: return null
        val dotted = m.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
        val build = m.groupValues[2].ifEmpty { return dotted }.toIntOrNull() ?: return null
        return dotted + build
    }

    /** "26.10.21b77" and "26.10.21 build 77" → "26.10.21.77"; any other version unchanged. */
    fun displayVersion(v: String): String = SUFFIXED_BUILD.replace(v) { "${it.groupValues[1]}.${it.groupValues[2]}" }

    fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val c = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }
}
