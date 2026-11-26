package com.ezragubbay.quire.di

import com.ezragubbay.quire.core.data.session.AccountDataCleaner
import com.ezragubbay.quire.core.data.session.LocalAccountDataCleaner
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.download.DownloadRepository
import com.ezragubbay.quire.feature.viewer.ViewerCache
import com.ezragubbay.quire.sync.OfflineSettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Sign-out data cleanup (#92, #101). */
@Module
@InstallIn(SingletonComponent::class)
object AccountModule {
    @Provides
    @Singleton
    fun accountDataCleaner(
        db: QuireDatabase,
        downloads: DownloadRepository,
        viewerCache: ViewerCache,
        offline: OfflineSettingsStore,
    ): AccountDataCleaner = LocalAccountDataCleaner(db, downloads, viewerCache, offline)
}
