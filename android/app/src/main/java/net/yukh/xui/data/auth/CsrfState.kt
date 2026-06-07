package net.yukh.xui.data.auth

/**
 * Thread-safe holder for the active session's CSRF token. Updated each time
 * the panel hands one back (initial GET, then re-fetched after login).
 */
class CsrfState {
    @Volatile
    private var token: String? = null

    fun set(value: String?) {
        token = value?.takeIf { it.isNotBlank() }
    }

    fun get(): String? = token
}
