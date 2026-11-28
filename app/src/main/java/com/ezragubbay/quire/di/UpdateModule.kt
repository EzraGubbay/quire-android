package com.ezragubbay.quire.di

import android.content.Context
import com.ezragubbay.quire.BuildConfig
import com.ezragubbay.quire.core.data.settings.settingsDataStore
import com.ezragubbay.quire.core.update.GitHubReleaseSource
import com.ezragubbay.quire.core.update.LatestReleaseSource
import com.ezragubbay.quire.core.update.UpdateChecker
import com.ezragubbay.quire.core.update.UpdateStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * Where the latest release comes from: GitHub, on its own interceptor-free client. A separate module so
 * instrumented tests replace it (`@TestInstallIn`) and never reach `api.github.com`.
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateSourceModule {
    @Provides
    @Singleton
    fun latestReleaseSource(
        @ApplicationContext context: Context,
    ): LatestReleaseSource = GitHubReleaseSource(GitHubReleaseSource.httpClient(File(context.cacheDir, "update-check")))
}

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {
    @Provides
    @Singleton
    fun updateChecker(
        @ApplicationContext context: Context,
        source: LatestReleaseSource,
    ): UpdateChecker = UpdateChecker(source, UpdateStore(context.settingsDataStore), BuildConfig.VERSION_NAME)
}
