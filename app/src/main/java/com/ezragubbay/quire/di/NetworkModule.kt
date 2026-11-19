package com.ezragubbay.quire.di

import com.ezragubbay.quire.BuildConfig
import com.ezragubbay.quire.core.network.NetworkOptions
import com.ezragubbay.quire.core.network.QuireClient
import com.ezragubbay.quire.core.network.QuireClientFactory
import com.ezragubbay.quire.core.network.ServerConfigProvider
import com.ezragubbay.quire.core.network.SessionEvents
import com.ezragubbay.quire.core.network.TokenCache
import com.ezragubbay.quire.core.network.TokenProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Network graph. [TokenProvider] and [ServerConfigProvider] are bound by the secure-storage module (#25);
 * this module only consumes them.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun sessionEvents(): SessionEvents = SessionEvents()

    /** Inject this to call `invalidate()` on sign-out or after pairing a new token. */
    @Provides
    @Singleton
    fun tokenCache(tokens: TokenProvider): TokenCache = TokenCache(tokens)

    @Provides
    @Singleton
    fun quireClient(
        config: ServerConfigProvider,
        tokens: TokenCache,
        events: SessionEvents,
    ): QuireClient =
        QuireClientFactory.create(
            config = config,
            tokens = tokens,
            events = events,
            options = NetworkOptions(allowCleartextLocal = BuildConfig.DEBUG, debugLogging = BuildConfig.DEBUG),
        )
}
