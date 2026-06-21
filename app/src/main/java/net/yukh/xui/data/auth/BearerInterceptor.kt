package net.yukh.xui.data.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer <token>` to every outgoing request.
 *
 * The token is supplied as a lambda so it can be swapped at runtime when
 * the user updates their connection profile, without rebuilding OkHttp.
 */
class BearerInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val req = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(req)
    }
}
