package com.ezragubbay.quire.di

import android.content.Context
import com.ezragubbay.quire.BuildConfig
import com.ezragubbay.quire.core.data.session.SessionRepository
import com.ezragubbay.quire.core.data.session.SessionState
import com.ezragubbay.quire.core.data.settings.settingsDataStore
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.network.NetworkOptions
import com.ezragubbay.quire.core.network.QuireClient
import com.ezragubbay.quire.core.network.QuireClientFactory
import com.ezragubbay.quire.core.network.ServerConfigProvider
import com.ezragubbay.quire.core.network.SessionEvents
import com.ezragubbay.quire.core.network.TokenCache
import com.ezragubbay.quire.push.AllCategoriesOn
import com.ezragubbay.quire.push.AppPushSession
import com.ezragubbay.quire.push.CachedPushNamesLookup
import com.ezragubbay.quire.push.DataStoreStaleTokenFlag
import com.ezragubbay.quire.push.FcmTokenSource
import com.ezragubbay.quire.push.FirebaseTokenSource
import com.ezragubbay.quire.push.NoopTokenSource
import com.ezragubbay.quire.push.NotificationPromptStore
import com.ezragubbay.quire.push.PushCategoryPolicy
import com.ezragubbay.quire.push.PushClient
import com.ezragubbay.quire.push.PushDevice
import com.ezragubbay.quire.push.PushMessageHandler
import com.ezragubbay.quire.push.PushNamesLookup
import com.ezragubbay.quire.push.PushNotificationFactory
import com.ezragubbay.quire.push.PushPreferences
import com.ezragubbay.quire.push.PushRegistrar
import com.ezragubbay.quire.push.PushRegistrationScheduler
import com.ezragubbay.quire.push.PushRegistrationWorkerFactory
import com.ezragubbay.quire.push.PushSession
import com.ezragubbay.quire.push.PushSyncTrigger
import com.ezragubbay.quire.push.ResourcePushTexts
import com.ezragubbay.quire.push.SystemPushNotifier
import com.ezragubbay.quire.push.WorkManagerPushRegistrationScheduler
import com.ezragubbay.quire.push.WorkManagerPushSyncTrigger
import com.ezragubbay.quire.push.notificationsAllowed
import com.ezragubbay.quire.push.pushRestApi
import com.ezragubbay.quire.sync.SyncSettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Binds the only Firebase touch point (ADR 0003 §4.1): the real token source only when the build had
 * `google-services.json`. Instrumented tests replace this module with a fake (`@TestInstallIn`).
 */
@Module
@InstallIn(SingletonComponent::class)
object PushTokenModule {
    @Provides
    @Singleton
    fun fcmTokenSource(): FcmTokenSource = if (BuildConfig.PUSH_CONFIGURED) FirebaseTokenSource() else NoopTokenSource
}

/** Push graph (QMOB-E8-S3): registration lifecycle, receiving, channels and the permission card. */
@Module
@InstallIn(SingletonComponent::class)
object PushModule {
    @Provides
    @Singleton
    fun pushClient(
        config: ServerConfigProvider,
        tokens: TokenCache,
        events: SessionEvents,
    ): PushClient =
        PushClient(
            pushRestApi(
                QuireClientFactory.okHttpClient(
                    config,
                    tokens,
                    events,
                    NetworkOptions(
                        allowCleartextLocal = BuildConfig.ALLOW_CLEARTEXT_LOCAL,
                        debugLogging = BuildConfig.DEBUG,
                    ),
                ),
            ),
        )

    /** The in-app category switches; QMOB-E8-S5 (#65) replaces this with the DataStore-backed toggles. */
    @Provides
    fun pushPreferences(): PushPreferences = AllCategoriesOn

    @Provides
    @Singleton
    fun pushRegistrationScheduler(
        @ApplicationContext context: Context,
    ): PushRegistrationScheduler = WorkManagerPushRegistrationScheduler(context)

    @Provides
    fun pushCategoryPolicy(
        @ApplicationContext context: Context,
        preferences: PushPreferences,
    ) = PushCategoryPolicy({ notificationsAllowed(context) }, preferences)

    @Provides
    fun pushDevice(
        @ApplicationContext context: Context,
        sessions: SessionRepository,
    ) = PushDevice(BuildConfig.VERSION_NAME, DataStoreStaleTokenFlag(context.settingsDataStore)) {
        sessions.current() is SessionState.SignedIn
    }

    @Provides
    @Singleton
    fun pushSession(
        registrar: PushRegistrar,
        prompt: NotificationPromptStore,
        @ApplicationScope scope: CoroutineScope,
    ): PushSession = AppPushSession(registrar, prompt, scope)

    /** Lazy: WorkManager builds the factory while the graph may still be initialising. */
    @Provides
    @Singleton
    fun pushRegistrationWorkerFactory(registrar: Provider<PushRegistrar>) =
        PushRegistrationWorkerFactory { registrar.get() }

    @Provides
    @Singleton
    fun pushNamesLookup(
        db: QuireDatabase,
        browse: QuireClient,
        push: PushClient,
    ): PushNamesLookup = CachedPushNamesLookup(db, browse, push)

    @Provides
    @Singleton
    fun pushSyncTrigger(
        @ApplicationContext context: Context,
        settings: SyncSettingsStore,
    ): PushSyncTrigger = WorkManagerPushSyncTrigger(context, settings)

    @Provides
    @Singleton
    fun pushMessageHandler(
        @ApplicationContext context: Context,
        preferences: PushPreferences,
        lookup: PushNamesLookup,
        sync: PushSyncTrigger,
    ) = PushMessageHandler(
        preferences = preferences,
        lookup = lookup,
        factory = PushNotificationFactory(ResourcePushTexts(context)),
        notifier = SystemPushNotifier(context),
        sync = sync,
    )
}
