package com.ezragubbay.quire.testing

import com.ezragubbay.quire.core.update.FetchResult
import com.ezragubbay.quire.core.update.LatestReleaseSource
import com.ezragubbay.quire.di.UpdateSourceModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Stands in for GitHub in every instrumented test, so no journey calls `api.github.com` and no update dialog can
 * cover a journey's UI. It answers "no release" unless a test sets [result].
 */
class FakeLatestReleaseSource : LatestReleaseSource {
    @Volatile
    var result: FetchResult = FetchResult.NoRelease

    @Volatile
    var calls: Int = 0
        private set

    override suspend fun fetch(): FetchResult {
        calls++
        return result
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [UpdateSourceModule::class])
object FakeUpdateModule {
    @Provides
    @Singleton
    fun fakeSource(): FakeLatestReleaseSource = FakeLatestReleaseSource()

    @Provides
    fun latestReleaseSource(fake: FakeLatestReleaseSource): LatestReleaseSource = fake
}
