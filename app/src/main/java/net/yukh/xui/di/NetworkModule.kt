package net.yukh.xui.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
        // Request bodies must include zero/false/empty fields verbatim — the
        // panel relies on e.g. id="" to auto-generate a UUID and enable=false
        // to actually disable. Without this kotlinx omits default-valued fields.
        encodeDefaults = true
    }
}
