package com.ezragubbay.quire

import android.app.Application
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import com.ezragubbay.quire.core.download.DownloadWorkerFactory
import com.ezragubbay.quire.push.PushChannels
import com.ezragubbay.quire.push.PushRegistrationWorkerFactory
import com.ezragubbay.quire.sync.SyncWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class QuireApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var downloadWorkerFactory: DownloadWorkerFactory

    @Inject lateinit var syncWorkerFactory: SyncWorkerFactory

    @Inject lateinit var pushRegistrationWorkerFactory: PushRegistrationWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // Push channels exist from the first launch, so their system settings can be opened before any push arrives.
        // A build without google-services.json can never receive a push, so it shows no empty push channels.
        if (BuildConfig.PUSH_CONFIGURED) PushChannels.ensure(this)
    }

    override val workManagerConfiguration: Configuration
        get() {
            // Each factory builds only its own worker class and returns null otherwise, so they chain.
            val factory =
                DelegatingWorkerFactory().apply {
                    addFactory(downloadWorkerFactory)
                    addFactory(syncWorkerFactory)
                    addFactory(pushRegistrationWorkerFactory)
                }
            return Configuration.Builder().setWorkerFactory(factory).build()
        }
}
