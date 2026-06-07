package net.yukh.xui.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import net.yukh.xui.data.auth.BearerInterceptor
import net.yukh.xui.data.auth.InsecureTls
import net.yukh.xui.data.prefs.ConnectionProfile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Builds a Retrofit-backed [XuiApi] bound to a specific connection profile.
 *
 * Each profile change rebuilds the client; OkHttp is cheap enough that this
 * is fine for the rare case of switching panels.
 */
object XuiApiFactory {

    fun create(profile: ConnectionProfile, json: Json): XuiApi {
        val httpBuilder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(BearerInterceptor { profile.token })
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )

        if (profile.allowInsecureTls) {
            InsecureTls.apply(httpBuilder)
        }

        return Retrofit.Builder()
            .baseUrl(profile.baseUrl)
            .client(httpBuilder.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(XuiApi::class.java)
    }
}
