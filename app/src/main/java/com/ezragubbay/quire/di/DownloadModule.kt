package com.ezragubbay.quire.di

import android.content.Context
import com.ezragubbay.quire.core.database.DownloadDao
import com.ezragubbay.quire.core.download.DownloadDeps
import com.ezragubbay.quire.core.download.DownloadPaths
import com.ezragubbay.quire.core.download.DownloadRepository
import com.ezragubbay.quire.core.download.DownloadScheduler
import com.ezragubbay.quire.core.download.WorkManagerDownloadScheduler
import com.ezragubbay.quire.core.network.ContentApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    ) = DownloadDeps(dao, api, paths)

    @Provides
    @Singleton
    fun downloadRepository(
        dao: DownloadDao,
        scheduler: DownloadScheduler,
        paths: DownloadPaths,
    ) = DownloadRepository(dao, scheduler, paths)
}
