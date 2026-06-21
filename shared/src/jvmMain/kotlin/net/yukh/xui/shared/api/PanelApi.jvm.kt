package net.yukh.xui.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

// `allowInsecure` is honored on iOS (Darwin server-trust override). The JVM
// engine exists only to satisfy the shared module's JVM target — it is not used
// by the iOS app — so it ignores the flag to keep the expect/actual signature.
actual fun platformHttpClient(allowInsecure: Boolean, block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) { block() }
