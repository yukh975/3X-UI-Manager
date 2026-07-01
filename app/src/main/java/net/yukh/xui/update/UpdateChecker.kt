package net.yukh.xui.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/** A release published on GitLab. */
data class AppRelease(
    val version: String,   // e.g. "0.5.5" (the tag without the leading "v")
    val notes: String,     // release description (markdown)
    val apkUrl: String?,   // download URL of the .apk asset, or null if missing
    val pageUrl: String,   // release web page (fallback link)
)

/**
 * Checks the app's own GitLab releases for a newer build. The project is public,
 * so the releases API and package downloads work anonymously — no token in the app.
 */
object UpdateChecker {
    private const val API = "https://git.home.yukh.net/api/v4/projects/19"
    const val RELEASES_PAGE = "https://git.home.yukh.net/yukh/3X-UI-Manager/-/releases"

    private val client = OkHttpClient()

    /** The latest release if it is strictly newer than [current], else null. */
    suspend fun latestIfNewer(current: String): AppRelease? {
        val latest = fetchLatest() ?: return null
        return if (isNewer(latest.version, current)) latest else null
    }

    /** The latest release regardless of the running version (for a manual check). */
    suspend fun fetchLatest(): AppRelease? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$API/releases?per_page=1").build()
        runCatching {
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
                        .ifBlank { RELEASES_PAGE },
                )
            }
        }.getOrNull()
    }

    /** true if [latest] > [current] by numeric semver (non-digit suffixes ignored). */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parse(latest)
        val b = parse(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parse(v: String): List<Int> =
        v.trim().removePrefix("v").split('.', '-', '+').mapNotNull { part ->
            part.takeWhile(Char::isDigit).toIntOrNull()
        }
}
