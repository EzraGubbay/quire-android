package com.ezragubbay.quire.di

import android.content.Context
import com.ezragubbay.quire.core.network.ContentApi
import com.ezragubbay.quire.feature.viewer.ViewerCache
import com.ezragubbay.quire.feature.viewer.ViewerStreamer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ViewerModule {
    @Provides
    @Singleton
    fun viewerCache(
        @ApplicationContext context: Context,
    ) = ViewerCache(context.cacheDir)

    @Provides
    @Singleton
    fun viewerStreamer(
        api: ContentApi,
        cache: ViewerCache,
    ) = ViewerStreamer(api, cache)
}
