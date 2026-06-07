package net.yukh.xui.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import net.yukh.xui.data.auth.BearerInterceptor
import net.yukh.xui.data.auth.CsrfInterceptor
import net.yukh.xui.data.auth.CsrfState
import net.yukh.xui.data.auth.InsecureTls
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Builds a Retrofit-backed [XuiApi] for one of the two supported auth modes.
 *
 * The factory is intentionally dumb: it does NOT perform login or token
 * validation. That's the repository's job — the factory just wires the
 * right interceptors so subsequent calls are authenticated.
 */
object XuiApiFactory {

    fun tokenAuthed(
        baseUrl: String,
        allowInsecureTls: Boolean,
        token: String,
        json: Json,
    ): XuiApi {
        val client = baseClient(allowInsecureTls)
            .addInterceptor(BearerInterceptor { token })
            .build()
        return makeRetrofit(baseUrl, client, json).create(XuiApi::class.java)
    }

    fun sessionAuthed(
        baseUrl: String,
        allowInsecureTls: Boolean,
        cookieJar: CookieJar,
        csrf: CsrfState,
        json: Json,
    ): XuiApi {
        val client = baseClient(allowInsecureTls)
            .cookieJar(cookieJar)
            .addInterceptor(CsrfInterceptor(csrf))
            .build()
        return makeRetrofit(baseUrl, client, json).create(XuiApi::class.java)
    }

    private fun baseClient(allowInsecureTls: Boolean): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
        if (allowInsecureTls) InsecureTls.apply(builder)
        return builder
    }

    private fun makeRetrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
