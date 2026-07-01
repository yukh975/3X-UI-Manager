package net.yukh.xui.shared.api

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A release published on the app's GitLab. */
data class AppUpdate(
    val version: String,   // tag without the leading "v", e.g. "0.5.5"
    val notes: String,     // release description (markdown)
    val pageUrl: String,   // release web page (opened in the browser)
)

@Serializable
private data class GitlabRelease(
    @SerialName("tag_name") val tagName: String = "",
    val description: String = "",
    @SerialName("_links") val links: Links? = null,
) {
    @Serializable
    data class Links(val self: String = "")
}

/**
 * Checks the app's own GitLab releases for a newer build. The project is public,
 * so the releases API works anonymously — no token. iOS can't install an unsigned
 * .ipa itself, so callers use this to notify and open the release page.
 */
object UpdateChecker {
    private const val API = "https://git.home.yukh.net/api/v4/projects/19"
    const val RELEASES_PAGE = "https://git.home.yukh.net/yukh/3X-UI-Manager/-/releases"

    /** The latest release if strictly newer than [current], else null. */
    suspend fun latestIfNewer(current: String): AppUpdate? {
        val latest = fetchLatest() ?: return null
        return if (isNewer(latest.version, current)) latest else null
    }

    /** The latest release regardless of the running version (for a manual check). */
    suspend fun fetchLatest(): AppUpdate? {
        val client = platformHttpClient(allowInsecure = false) {
            install(ContentNegotiation) { json(sharedJson) }
        }
        return try {
            val list: List<GitlabRelease> = client.get("$API/releases?per_page=1").body()
            val rel = list.firstOrNull() ?: return null
            val version = rel.tagName.removePrefix("v")
            if (version.isBlank()) null
            else AppUpdate(
                version = version,
                notes = rel.description,
                pageUrl = rel.links?.self?.takeIf { it.isNotBlank() } ?: RELEASES_PAGE,
            )
        } catch (e: Throwable) {
            null
        } finally {
            client.close()
        }
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
