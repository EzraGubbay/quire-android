package com.ezragubbay.quire.di

import android.content.Context
import com.ezragubbay.quire.core.data.session.KeystoreCipher
import com.ezragubbay.quire.core.data.session.PendingPairing
import com.ezragubbay.quire.core.data.session.SessionRepository
import com.ezragubbay.quire.core.data.session.SessionState
import com.ezragubbay.quire.core.data.session.sessionDataStore
import com.ezragubbay.quire.core.network.ServerConfigProvider
import com.ezragubbay.quire.core.network.TokenProvider
import com.ezragubbay.quire.feature.auth.DefaultPairer
import com.ezragubbay.quire.feature.auth.Pairer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Session graph: binds the secure-storage layer (#25) to the network layer's provider seams (#24).
 * See `docs/adr/0001-di-strategy.md`.
 */
@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun sessionRepository(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): SessionRepository = SessionRepository(context.sessionDataStore, KeystoreCipher(), scope)

    /**
     * Suspend-to-sync bridge: the network layer's [TokenProvider] is synchronous but [SessionRepository.token]
     * suspends. It runs on OkHttp's IO threads (never main), and the singleton `TokenCache` in front of it means
     * only the first request after launch/sign-in blocks. A token minted by an in-flight pairing wins.
     */
    @Provides
    @Singleton
    fun tokenProvider(
        sessions: SessionRepository,
        pending: PendingPairing,
    ): TokenProvider = TokenProvider { pending.token ?: runBlocking { sessions.token() } }

    /**
     * Read per request, synchronously, from the session state. [SessionRepository.state] starts as signed-out
     * until its first disk read lands, so a signed-out value falls back to the authoritative read once.
     */
    @Provides
    @Singleton
    fun serverConfigProvider(
        sessions: SessionRepository,
        pending: PendingPairing,
    ): ServerConfigProvider =
        ServerConfigProvider {
            pending.serverUrl ?: signedInServerUrl(sessions)
        }

    private fun signedInServerUrl(sessions: SessionRepository): String? {
        val state =
            sessions.state.value.takeUnless { it is SessionState.SignedOut } ?: runBlocking { sessions.current() }
        return (state as? SessionState.SignedIn)?.serverUrl
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface PairingModule {
    @Binds
    fun pairer(impl: DefaultPairer): Pairer
}
