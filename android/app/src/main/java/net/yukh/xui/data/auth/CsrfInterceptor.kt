package net.yukh.xui.data.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `X-CSRF-Token: <token>` to every unsafe request once the holder has a
 * value. Skips GET/HEAD/OPTIONS which the panel exempts from CSRF anyway.
 *
 * Bearer-authenticated clients should NOT install this interceptor — the
 * panel skips CSRF for Bearer-authed callers, so an empty/stale CSRF header
 * would just be wasted bytes.
 */
class CsrfInterceptor(private val state: CsrfState) : Interceptor {

    private val unsafeMethods = setOf("POST", "PUT", "PATCH", "DELETE")

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.method !in unsafeMethods) return chain.proceed(req)
        val token = state.get() ?: return chain.proceed(req)
        val withCsrf = req.newBuilder().addHeader("X-CSRF-Token", token).build()
        return chain.proceed(withCsrf)
    }
}
