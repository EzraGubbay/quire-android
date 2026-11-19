package com.ezragubbay.quire

import android.app.Application
import androidx.work.Configuration
import com.ezragubbay.quire.core.download.DownloadWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class QuireApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var downloadWorkerFactory: DownloadWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(downloadWorkerFactory).build()
}
