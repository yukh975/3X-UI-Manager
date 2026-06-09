@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.yukh.xui.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.NSURLSessionTask
import platform.Foundation.create
import platform.Foundation.serverTrust
import platform.Security.SecTrustRef

actual fun platformHttpClient(allowInsecure: Boolean, block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Darwin) {
        block()
        if (allowInsecure) {
            engine {
                // Trust the server's certificate unconditionally — mirrors the
                // Android app's "allow self-signed TLS". Only the server-trust
                // challenge is short-circuited; other auth methods fall through.
                handleChallenge { _: NSURLSession, _: NSURLSessionTask, challenge: NSURLAuthenticationChallenge, completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit ->
                    val trust: SecTrustRef? = challenge.protectionSpace.serverTrust
                    val credential = trust?.let { NSURLCredential.create(trust = it) }
                    completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
                }
            }
        }
    }
