package com.ezragubbay.quire.testing

import com.ezragubbay.quire.di.PushTokenModule
import com.ezragubbay.quire.push.FcmTokenSource
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.Collections
import javax.inject.Singleton

/**
 * Stands in for Firebase in every instrumented test (no `google-services.json`, no real FCM). It starts
 * **unavailable**, exactly like a debug build without the file, so the other journeys see push as off and the
 * hermetic server never gets push requests they did not queue. `PushJourneyTest` switches it on with [available].
 */
class FakeFcmTokenSource : FcmTokenSource {
    @Volatile
    override var available: Boolean = false

    /** Token calls in order: `token`, `delete`, `autoInit=<bool>`. */
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Volatile
    var autoInit: Boolean? = null
        private set

    override suspend fun token(): String {
        calls += "token"
        return TOKEN
    }

    override suspend fun delete() {
        calls += "delete"
    }

    override fun setAutoInitEnabled(enabled: Boolean) {
        calls += "autoInit=$enabled"
        autoInit = enabled
    }

    companion object {
        const val TOKEN = "fake-fcm-token"
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PushTokenModule::class])
object FakePushTokenModule {
    @Provides
    @Singleton
    fun fakeTokenSource(): FakeFcmTokenSource = FakeFcmTokenSource()

    @Provides
    fun fcmTokenSource(fake: FakeFcmTokenSource): FcmTokenSource = fake
}
