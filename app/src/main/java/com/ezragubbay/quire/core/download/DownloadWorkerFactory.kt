package com.ezragubbay.quire.core.download

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import javax.inject.Inject
import javax.inject.Singleton

/** Builds [DownloadWorker]; wired into WorkManager by `QuireApplication`. Other worker classes fall through. */
@Singleton
class DownloadWorkerFactory
    @Inject
    constructor(
        private val deps: DownloadDeps,
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? =
            if (workerClassName == DownloadWorker::class.java.name) {
                DownloadWorker(appContext, workerParameters, deps)
            } else {
                null
            }
    }
