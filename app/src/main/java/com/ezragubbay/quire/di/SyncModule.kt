package com.ezragubbay.quire.di

import android.content.Context
import com.ezragubbay.quire.core.data.course.CourseRepository
import com.ezragubbay.quire.core.data.session.SessionRepository
import com.ezragubbay.quire.core.data.session.SessionState
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.download.DownloadRepository
import com.ezragubbay.quire.sync.CourseSyncSource
import com.ezragubbay.quire.sync.OfflinePinner
import com.ezragubbay.quire.sync.OfflineSettingsStore
import com.ezragubbay.quire.sync.PinController
import com.ezragubbay.quire.sync.PinnedDownloadNotifier
import com.ezragubbay.quire.sync.RepositoryCourseSyncSource
import com.ezragubbay.quire.sync.RoomPinStorage
import com.ezragubbay.quire.sync.SyncCoordinator
import com.ezragubbay.quire.sync.SyncDeps
import com.ezragubbay.quire.sync.SyncRunner
import com.ezragubbay.quire.sync.SyncScheduler
import com.ezragubbay.quire.sync.SyncSettingsStore
import com.ezragubbay.quire.sync.SyncWorkerFactory
import com.ezragubbay.quire.sync.WorkManagerSyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** Background metadata sync graph (QMOB-E7-S1). */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun syncSettingsStore(
        @ApplicationContext context: Context,
    ) = SyncSettingsStore(context)

    @Provides
    @Singleton
    fun syncScheduler(
        @ApplicationContext context: Context,
    ): SyncScheduler = WorkManagerSyncScheduler(context)

    @Provides
    @Singleton
    fun courseSyncSource(
        courses: CourseRepository,
        db: QuireDatabase,
    ): CourseSyncSource = RepositoryCourseSyncSource(courses, db.courseDao())

    @Provides
    @Singleton
    fun syncRunner(
        source: CourseSyncSource,
        store: SyncSettingsStore,
        sessions: SessionRepository,
        pinned: OfflinePinner,
    ) = SyncRunner(source, store, isSignedIn = { sessions.current() is SessionState.SignedIn }, pinned = pinned)

    @Provides
    @Singleton
    fun offlineSettingsStore(
        @ApplicationContext context: Context,
    ) = OfflineSettingsStore(context)

    @Provides
    @Singleton
    fun offlinePinner(
        settings: OfflineSettingsStore,
        syncStore: SyncSettingsStore,
        db: QuireDatabase,
        downloads: DownloadRepository,
    ) = OfflinePinner(
        settings,
        RoomPinStorage(db.fileDao(), db.downloadDao(), downloads),
        wifiOnly = { syncStore.current().wifiOnly },
    )

    @Provides
    fun pinController(pinner: OfflinePinner): PinController = pinner

    @Provides
    @Singleton
    fun pinnedDownloadNotifier(
        @ApplicationContext context: Context,
        settings: OfflineSettingsStore,
        db: QuireDatabase,
    ) = PinnedDownloadNotifier(
        context,
        settings,
    ) { slug ->
        db
            .courseDao()
            .getAll()
            .firstOrNull { it.slug == slug }
            ?.name
    }

    @Provides
    @Singleton
    fun syncWorkerFactory(runner: SyncRunner) = SyncWorkerFactory(SyncDeps(runner))

    @Provides
    @Singleton
    fun syncCoordinator(
        store: SyncSettingsStore,
        scheduler: SyncScheduler,
        @ApplicationScope scope: CoroutineScope,
    ) = SyncCoordinator(store, scheduler, scope)
}
