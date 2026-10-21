package net.yukh.xui.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** A release published on one of the update channels. */
data class AppRelease(
    val version: String,   // e.g. "26.10.21b77" (the tag without the leading "v")
    val notes: String,     // release description (markdown)
    val changes: List<VersionNotes> = emptyList(),  // changelog since the installed version, newest first
    val apkUrl: String?,   // download URL of the .apk asset, or null if missing
    val pageUrl: String,   // release web page (fallback link)
)

/**
 * Where the self-updater looks for a newer build. STABLE is the public GitHub
 * repo, reachable from anywhere — the normal channel. TESTING is the home GitLab
 * (the build factory), reachable only on the home network, for dogfooding a build
 * before it is published to GitHub.
 */
enum class UpdateChannel {
    STABLE, TESTING;

    companion object {
        const val STABLE_KEY = "stable"
        const val TESTING_KEY = "testing"

        fun from(key: String?): UpdateChannel = if (key == TESTING_KEY) TESTING else STABLE
    }
}

/**
 * Checks the app's own releases for a newer build. Both channels are public and
 * work anonymously — no token in the app.
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

    private val client = OkHttpClient()

    fun releasesPage(channel: UpdateChannel): String =
        if (channel == UpdateChannel.TESTING) GL_RELEASES else GH_RELEASES

    /** The latest release if it is strictly newer than [current], else null. */
    suspend fun latestIfNewer(current: String, channel: UpdateChannel): AppRelease? {
        val latest = fetchLatest(channel) ?: return null
        return if (isNewer(latest.version, current)) latest else null
    }

    /**
     * The whole changelog in the app's language at tag v[version] — the release body
     * is English-only, but we keep a Russian changelog too, so the "what's new"
     * shown in the dialog can match the UI language. Read from the latest tag, it
     * holds every version the user may have skipped. Returns null on any failure →
     * caller keeps the release body as a fallback.
     */
    suspend fun localizedChangelog(version: String, russian: Boolean, channel: UpdateChannel): String? =
        withContext(Dispatchers.IO) {
            val file = if (russian) "CHANGELOG.ru.md" else "CHANGELOG.md"
            val url = if (channel == UpdateChannel.TESTING) {
                "$GL_FILES/$file/raw?ref=v$version"
            } else {
                "$GH_RAW/v$version/$file"
            }
            val req = Request.Builder().url(url).build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                }
            }.getOrNull()
        }

    private val NUMBERED = Regex("^\\d+[.)]\\s")
    private val BLANK_RUN = Regex("\n{3,}")

    /**
     * Un-wrap the changelog's hard line breaks for display: the source is wrapped
     * at ~80 columns, so shown verbatim it breaks mid-sentence. Join each list
     * item / paragraph's continuation lines into one logical line and let the
     * dialog soft-wrap. Headings (`#`), list items (`-`/`*`/`+`/`N.`), block
     * quotes and blank lines keep their own line.
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
    suspend fun fetchLatest(channel: UpdateChannel): AppRelease? = withContext(Dispatchers.IO) {
        if (channel == UpdateChannel.TESTING) fetchLatestGitLab() else fetchLatestGitHub()
    }

    /** GitHub `/releases/latest` (excludes drafts and pre-releases). */
    private fun fetchLatestGitHub(): AppRelease? {
        val req = Request.Builder().url("$GH_API/releases/latest")
            .header("Accept", "application/vnd.github+json").build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val rel = JSONObject(resp.body?.string() ?: return@use null)
                val version = rel.optString("tag_name").removePrefix("v")
                if (version.isBlank()) return@use null
                val assets = rel.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        val name = a.optString("name")
                        // The standard signed APK, not the F-Droid reproducible one.
                        if (name.endsWith(".apk") && name != "fdroid.apk") {
                            apkUrl = a.optString("browser_download_url"); break
                        }
                    }
                }
                AppRelease(
                    version = version,
                    notes = rel.optString("body"),
                    apkUrl = apkUrl,
                    pageUrl = rel.optString("html_url").ifBlank { GH_RELEASES },
                )
            }
        }.getOrNull()
    }

    /** Home GitLab `/releases?per_page=1` (newest, including any pre-release). */
    private fun fetchLatestGitLab(): AppRelease? {
        val req = Request.Builder().url("$GL_API/releases?per_page=1").build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val arr = JSONArray(resp.body?.string() ?: return@use null)
                if (arr.length() == 0) return@use null
                val rel = arr.getJSONObject(0)
                val version = rel.optString("tag_name").removePrefix("v")
                if (version.isBlank()) return@use null
                val links = rel.optJSONObject("assets")?.optJSONArray("links")
                var apkUrl: String? = null
                if (links != null) {
                    for (i in 0 until links.length()) {
                        val url = links.getJSONObject(i).optString("url")
                        if (url.endsWith(".apk")) { apkUrl = url; break }
                    }
                }
                AppRelease(
                    version = version,
                    notes = rel.optString("description"),
                    apkUrl = apkUrl,
                    pageUrl = rel.optJSONObject("_links")?.optString("self").orEmpty()
                        .ifBlank { GL_RELEASES },
                )
            }
        }.getOrNull()
    }

    /** true if [latest] > [current]: date, then build number ("26.10.21b78" > "26.10.21 build 77" > "0.15.6"). */
    fun isNewer(latest: String, current: String): Boolean {
        val a = ChangelogNotes.parseVersion(latest) ?: return false
        val b = ChangelogNotes.parseVersion(current) ?: emptyList()
        return ChangelogNotes.compare(a, b) > 0
    }
}
