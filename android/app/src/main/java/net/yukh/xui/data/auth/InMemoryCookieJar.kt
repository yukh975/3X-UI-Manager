package net.yukh.xui.data.auth

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Lightweight cookie store for OkHttp.
 *
 * Lives for the app process — sessions don't survive a restart, which is the
 * point: 3x-ui sessions can expire, and re-deriving the session from saved
 * credentials at login time is more reliable than trying to track expiries.
 */
class InMemoryCookieJar : CookieJar {

    private val store: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { fresh ->
            list.removeAll { it.name == fresh.name && it.path == fresh.path }
            list.add(fresh)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val list = store[url.host] ?: return emptyList()
        list.removeAll { it.expiresAt in 1..now }
        return list.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        store.clear()
    }
}
