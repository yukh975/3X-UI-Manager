package net.yukh.xui.data.auth

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Trust-everyone SSL setup for users running a panel with a self-signed
 * certificate. Opt-in per connection profile — the default OkHttp config
 * remains strict.
 */
internal object InsecureTls {

    private val trustManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private fun socketFactory(): SSLSocketFactory {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf(trustManager), SecureRandom())
        return ctx.socketFactory
    }

    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder
        .sslSocketFactory(socketFactory(), trustManager)
        .hostnameVerifier { _, _ -> true }
}
