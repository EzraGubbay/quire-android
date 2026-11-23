package com.ezragubbay.quire

import android.app.Application
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import com.ezragubbay.quire.core.download.DownloadWorkerFactory
import com.ezragubbay.quire.sync.SyncWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class QuireApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var downloadWorkerFactory: DownloadWorkerFactory

    @Inject lateinit var syncWorkerFactory: SyncWorkerFactory

    override val workManagerConfiguration: Configuration
        get() {
            // Each factory builds only its own worker class and returns null otherwise, so they chain.
            val factory =
                DelegatingWorkerFactory().apply {
                    addFactory(downloadWorkerFactory)
                    addFactory(syncWorkerFactory)
                }
            return Configuration.Builder().setWorkerFactory(factory).build()
        }
}
