package net.yukh.xui.shared.api

/** One `## [X.Y.Z] — date` section of the changelog. */
data class VersionNotes(
    val version: String,
    val date: String,  // as written in the changelog heading, "" if absent
    val body: String,  // the section's markdown, ### subsections and bullets kept
)

/** The versions to show in the update dialog; [olderOmitted] = the total cap dropped older ones. */
data class ChangelogSelection(
    val versions: List<VersionNotes>,
    val olderOmitted: Boolean,
)

/**
 * Picks what an update dialog shows: every version newer than the installed one
 * up to the latest, newest first — people skip releases, and the changes of a
 * skipped version must not vanish. Pure, so the selection rules are testable.
 */
object ChangelogNotes {
    const val MAX_VERSION_CHARS = 2000
    const val MAX_TOTAL_CHARS = 8000
    const val UNKNOWN_INSTALLED_COUNT = 3

    private val HEADING = Regex("^## \\[([^\\]]+)\\]\\s*(?:[—–-]+\\s*(\\S+))?")

    /** All versioned sections of a Keep-a-Changelog file, in file order. `[Unreleased]` is skipped. */
    fun parse(changelog: String): List<VersionNotes> {
        val out = mutableListOf<VersionNotes>()
        var version: String? = null
        var date = ""
        val body = StringBuilder()
        fun flush() {
            val v = version ?: return
            out.add(VersionNotes(v, date, body.toString().trim()))
        }
        for (line in changelog.lines()) {
            if (line.startsWith("## [")) {
                flush()
                body.clear()
                val m = HEADING.find(line)
                val v = m?.groupValues?.get(1)?.trim().orEmpty()
                version = v.takeIf { UpdateChecker.versionParts(it).isNotEmpty() }
                date = m?.groupValues?.get(2).orEmpty()
            } else if (version != null) {
                body.append(line).append('\n')
            }
        }
        flush()
        return out
    }

    /**
     * Versions in (installed, latest], newest first, capped per version and in
     * total. Installed unparsable → the last [UNKNOWN_INSTALLED_COUNT]; installed
     * not older than latest → only latest. Empty if latest has no section.
     */
    fun select(all: List<VersionNotes>, installed: String, latest: String): ChangelogSelection {
        val upToLatest = all.filter { !UpdateChecker.isNewer(it.version, latest) && it.body.isNotBlank() }
            .sortedWith { a, b -> compareVersions(b.version, a.version) }
            .distinctBy { UpdateChecker.versionParts(it.version) }
        val newest = upToLatest.firstOrNull()
        if (newest == null || compareVersions(newest.version, latest) != 0) {
            return ChangelogSelection(emptyList(), false)
        }
        val wanted = when {
            UpdateChecker.versionParts(installed).isEmpty() -> upToLatest.take(UNKNOWN_INSTALLED_COUNT)
            !UpdateChecker.isNewer(latest, installed) -> listOf(newest)
            else -> upToLatest.filter { UpdateChecker.isNewer(it.version, installed) }
        }
        val kept = mutableListOf<VersionNotes>()
        var total = 0
        for (v in wanted) {
            val capped = v.copy(body = capBody(v.body, MAX_VERSION_CHARS))
            if (kept.isNotEmpty() && total + capped.body.length > MAX_TOTAL_CHARS) break
            kept.add(capped)
            total += capped.body.length
        }
        return ChangelogSelection(kept, kept.size < wanted.size)
    }

    private fun compareVersions(a: String, b: String): Int = when {
        UpdateChecker.isNewer(a, b) -> 1
        UpdateChecker.isNewer(b, a) -> -1
        else -> 0
    }

    /** Cut at the last line break within [max] chars so a bullet is not split mid-word. */
    private fun capBody(body: String, max: Int): String {
        if (body.length <= max) return body
        val cut = body.lastIndexOf('\n', max - 1).takeIf { it > max / 2 } ?: (max - 1)
        return body.substring(0, cut).trimEnd() + "\n…"
    }
}
