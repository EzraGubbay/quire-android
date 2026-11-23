package com.ezragubbay.quire.di

import com.ezragubbay.quire.core.network.BrowseApi
import com.ezragubbay.quire.push.ApiPushTargetResolver
import com.ezragubbay.quire.push.PushClient
import com.ezragubbay.quire.push.PushTargetResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Notification deep links (QMOB-E8-S4): the stale-target check before the shell navigates (ADR 0003 §4.5). */
@Module
@InstallIn(SingletonComponent::class)
object PushTargetModule {
    @Provides
    fun pushTargetResolver(
        browse: BrowseApi,
        push: PushClient,
    ): PushTargetResolver = ApiPushTargetResolver(browse, push)
}
