package com.ezragubbay.quire.core.network

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

data class NetworkOptions(
    val allowCleartextLocal: Boolean,
    val debugLogging: Boolean = false,
    val timeoutMillis: Long = 30_000L,
)

/** Builds the OkHttp + Retrofit stack. Used by Hilt's `NetworkModule` and directly by tests. */
object QuireClientFactory {
    /** Placeholder host: [BaseUrlInterceptor] replaces it with the runtime server URL on every request. */
    private const val PLACEHOLDER_BASE = "https://quire.invalid${BaseUrlInterceptor.API_PREFIX}/"

    fun okHttpClient(
        config: ServerConfigProvider,
        tokens: TokenProvider,
        events: SessionEvents,
        options: NetworkOptions,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .callTimeout(options.timeoutMillis, TimeUnit.MILLISECONDS)
            .connectTimeout(options.timeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(options.timeoutMillis, TimeUnit.MILLISECONDS)
            .addInterceptor(BaseUrlInterceptor(config, options.allowCleartextLocal))
            .addInterceptor(AuthInterceptor(tokens as? TokenCache ?: TokenCache(tokens), events))
            .apply {
                if (options.debugLogging) {
                    // Headers only (no bodies); Authorization is redacted so the token never reaches logcat.
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.HEADERS
                            redactHeader("Authorization")
                        },
                    )
                }
            }.build()

    fun api(client: Call.Factory): QuireApi =
        Retrofit
            .Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .callFactory(client)
            .addConverterFactory(QuireJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(QuireApi::class.java)

    fun create(
        config: ServerConfigProvider,
        tokens: TokenProvider,
        events: SessionEvents,
        options: NetworkOptions,
    ): QuireClient = QuireClient(api(okHttpClient(config, tokens, events, options)))
}
