package net.yukh.xui.shared.api

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A release published on one of the update channels. */
data class AppUpdate(
    val version: String,   // tag without the leading "v", e.g. "26.10.22.79"
    val notes: String,     // release description (markdown)
    val pageUrl: String,   // release web page (opened in the browser)
    // Per-version changelog sections since the installed build; empty → show [notes].
    val versions: List<VersionNotes> = emptyList(),
    val olderOmitted: Boolean = false,
)

/**
 * Where the updater looks for a newer build. STABLE is the public GitHub repo,
 * reachable from anywhere — the normal channel. TESTING is the home GitLab (the
 * build factory), reachable only on the home network, for pre-release builds.
 */
enum class UpdateChannel {
    STABLE, TESTING;

    companion object {
        const val STABLE_KEY = "stable"
        const val TESTING_KEY = "testing"

        fun from(key: String?): UpdateChannel = if (key == TESTING_KEY) TESTING else STABLE
    }
}

@Serializable
private data class GitlabRelease(
    @SerialName("tag_name") val tagName: String = "",
    val description: String = "",
    @SerialName("_links") val links: Links? = null,
) {
    @Serializable
    data class Links(val self: String = "")
}

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
)

/**
 * Checks the app's own releases for a newer build. Both channels are public and
 * work anonymously — no token. iOS can't install an unsigned .ipa itself, so
 * callers use this to notify and open the release page.
 */
object UpdateChecker {
    // Stable: public GitHub repo, reachable from anywhere.
    private const val GH_REPO = "yukh975/3X-UI-Manager"
    private const val GH_API = "https://api.github.com/repos/$GH_REPO"
    private const val GH_RAW = "https://raw.githubusercontent.com/$GH_REPO"
    private const val GH_RELEASES = "https://github.com/$GH_REPO/releases"

    // Testing: home GitLab, only on the home network.
    private const val GL_API = "https://git.home.yukh.net/api/v4/projects/19"
    private const val GL_FILES = "$GL_API/repository/files"
    private const val GL_RELEASES = "https://git.home.yukh.net/yukh/3X-UI-Manager/-/releases"

    fun releasesPage(channel: UpdateChannel): String =
        if (channel == UpdateChannel.TESTING) GL_RELEASES else GH_RELEASES

    /** The latest release if strictly newer than [current], else null. */
    suspend fun latestIfNewer(current: String, channel: UpdateChannel): AppUpdate? {
        val latest = fetchLatest(channel) ?: return null
        return if (isNewer(latest.version, current)) latest else null
    }

    /**
     * The changelog in the app's language at the [version] tag — the release body
     * is English-only, but we keep a Russian changelog too, so the "what's new"
     * shown in the dialog can match the UI language. Its sections feed
     * [ChangelogNotes]; null on any failure → caller keeps the release body.
     */
    suspend fun localizedChangelog(version: String, russian: Boolean, channel: UpdateChannel): String? {
        val file = if (russian) "CHANGELOG.ru.md" else "CHANGELOG.md"
        val url = if (channel == UpdateChannel.TESTING) {
            "$GL_FILES/$file/raw?ref=v$version"
        } else {
            "$GH_RAW/v$version/$file"
        }
        val client = platformHttpClient(allowInsecure = false) {}
        return try {
            val response = client.get(url)
            if (response.status.value in 200..299) response.bodyAsText() else null
        } catch (e: Throwable) {
            null
        } finally {
            client.close()
        }
    }

    private val NUMBERED = Regex("^\\d+[.)]\\s")
    private val BLANK_RUN = Regex("\n{3,}")

    /**
     * Un-wrap the changelog's hard line breaks for display: the source is wrapped
     * at ~80 columns, so shown verbatim it breaks mid-sentence. Join each list
     * item / paragraph's continuation lines into one logical line and let the
     * dialog soft-wrap. Headings, list items, block quotes and blank lines keep
     * their own line.
     */
    fun reflowNotes(md: String): String {
        val out = mutableListOf<String>()
        for (raw in md.lines()) {
            val t = raw.trim()
            val newBlock = t.isEmpty() || t.startsWith("#") || t.startsWith("- ") ||
                t.startsWith("* ") || t.startsWith("+ ") || t.startsWith("> ") ||
                NUMBERED.containsMatchIn(t)
            if (out.isEmpty() || newBlock) out.add(t) else out[out.lastIndex] = out.last() + " " + t
        }
        return out.joinToString("\n").replace(BLANK_RUN, "\n\n").trim()
    }

    /** The latest release regardless of the running version (for a manual check). */
    suspend fun fetchLatest(channel: UpdateChannel): AppUpdate? {
        val client = platformHttpClient(allowInsecure = false) {
            install(ContentNegotiation) { json(sharedJson) }
        }
        return try {
            if (channel == UpdateChannel.TESTING) {
                val list: List<GitlabRelease> = client.get("$GL_API/releases?per_page=1").body()
                val rel = list.firstOrNull() ?: return null
                val version = rel.tagName.removePrefix("v")
                if (version.isBlank()) null
                else AppUpdate(
                    version = version,
                    notes = rel.description,
                    pageUrl = rel.links?.self?.takeIf { it.isNotBlank() } ?: GL_RELEASES,
                )
            } else {
                val rel: GithubRelease = client.get("$GH_API/releases/latest").body()
                val version = rel.tagName.removePrefix("v")
                if (version.isBlank()) null
                else AppUpdate(
                    version = version,
                    notes = rel.body,
                    pageUrl = rel.htmlUrl.ifBlank { GH_RELEASES },
                )
            }
        } catch (e: Throwable) {
            null
        } finally {
            client.close()
        }
    }

    /** true if [latest] > [current]: date, then build number ("26.10.22.79" > "26.10.21 build 78" > "0.15.3"). */
    fun isNewer(latest: String, current: String): Boolean {
        val a = versionParts(latest)
        val b = versionParts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** "26.10.21b78" and "26.10.21 build 78" → "26.10.21.78"; any other version unchanged. */
    fun displayVersion(v: String): String = SUFFIXED_BUILD.replace(v) { "${it.groupValues[1]}.${it.groupValues[2]}" }

    private val SUFFIXED_BUILD = Regex("""^(\d+\.\d+\.\d+)(?:b| build )(\d+)$""")

    // b77 and b78 shipped as "26.10.21 build 77" with tag "v26.10.21b77" before the dotted form.
    private val VERSION = Regex("""^v?(\d+(?:\.\d+)*)(?:(?:b| build )(\d+))?""")

    /** Dotted part plus build ("26.10.21.77" and "26.10.21 build 77" → [26, 10, 21, 77], "0.15.3-debug" → [0, 15, 3]); empty if none. */
    internal fun versionParts(v: String): List<Int> {
        val m = VERSION.find(v.trim()) ?: return emptyList()
        val dotted = m.groupValues[1].split('.').map { it.toIntOrNull() ?: return emptyList() }
        val build = m.groupValues[2].ifEmpty { return dotted }.toIntOrNull() ?: return emptyList()
        return dotted + build
    }
}
