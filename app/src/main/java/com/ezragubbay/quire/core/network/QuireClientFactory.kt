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

    /** Streaming client for downloads: same interceptors, but no overall call timeout (reads still time out). */
    fun contentApi(
        config: ServerConfigProvider,
        tokens: TokenProvider,
        events: SessionEvents,
        options: NetworkOptions,
    ): ContentApi =
        Retrofit
            .Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .callFactory(
                okHttpClient(config, tokens, events, options)
                    .newBuilder()
                    .callTimeout(0, TimeUnit.MILLISECONDS)
                    .build(),
            ).build()
            .create(ContentApi::class.java)

    /**
     * Streaming client for chat answers: no overall call timeout (an answer may take minutes) but a read timeout of
     * [idleMillis], which acts as the idle watchdog: that long without bytes fails the read with an I/O error.
     */
    fun chatStreamApi(
        config: ServerConfigProvider,
        tokens: TokenProvider,
        events: SessionEvents,
        options: NetworkOptions,
        idleMillis: Long = CHAT_IDLE_MILLIS,
    ): ChatStreamApi =
        Retrofit
            .Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .callFactory(
                okHttpClient(config, tokens, events, options)
                    .newBuilder()
                    .callTimeout(0, TimeUnit.MILLISECONDS)
                    .readTimeout(idleMillis, TimeUnit.MILLISECONDS)
                    .build(),
            ).addConverterFactory(QuireJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ChatStreamApi::class.java)

    const val CHAT_IDLE_MILLIS = 60_000L

    fun chatApi(client: Call.Factory): ChatApi =
        ChatClient(
            Retrofit
                .Builder()
                .baseUrl(PLACEHOLDER_BASE)
                .callFactory(client)
                .addConverterFactory(QuireJson.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(ChatRestApi::class.java),
        )

    fun create(
        config: ServerConfigProvider,
        tokens: TokenProvider,
        events: SessionEvents,
        options: NetworkOptions,
    ): QuireClient = QuireClient(api(okHttpClient(config, tokens, events, options)))
}
