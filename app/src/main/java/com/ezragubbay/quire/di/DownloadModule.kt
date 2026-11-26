package com.ezragubbay.quire.di

import android.content.Context
import com.ezragubbay.quire.core.database.DownloadDao
import com.ezragubbay.quire.core.download.DownloadDeps
import com.ezragubbay.quire.core.download.DownloadPaths
import com.ezragubbay.quire.core.download.DownloadProgressNotifier
import com.ezragubbay.quire.core.download.DownloadRepository
import com.ezragubbay.quire.core.download.DownloadScheduler
import com.ezragubbay.quire.core.download.SystemDownloadNotificationPoster
import com.ezragubbay.quire.core.download.WorkManagerDownloadScheduler
import com.ezragubbay.quire.core.network.ContentApi
import com.ezragubbay.quire.sync.PinnedDownloadNotifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    @Provides
    @Singleton
    fun downloadPaths(
        @ApplicationContext context: Context,
    ) = DownloadPaths(context.filesDir)

    @Provides
    @Singleton
    fun downloadScheduler(
        @ApplicationContext context: Context,
    ): DownloadScheduler = WorkManagerDownloadScheduler(context)

    @Provides
    @Singleton
    fun downloadDeps(
        dao: DownloadDao,
        api: ContentApi,
        paths: DownloadPaths,
        notifier: PinnedDownloadNotifier,
    ) = DownloadDeps(dao, api, paths, onSettled = notifier::onDownloadSettled)

    @Provides
    @Singleton
    fun downloadRepository(
        dao: DownloadDao,
        scheduler: DownloadScheduler,
        paths: DownloadPaths,
    ) = DownloadRepository(dao, scheduler, paths)

    @Provides
    @Singleton
    fun downloadProgressNotifier(
        dao: DownloadDao,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ) = DownloadProgressNotifier(dao.observeAll(), SystemDownloadNotificationPoster(context), scope)
}
