package com.ezragubbay.quire.core.upload

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import javax.inject.Inject
import javax.inject.Singleton

/** Builds [UploadWorker]; wired into WorkManager by `QuireApplication`. Other worker classes fall through. */
@Singleton
class UploadWorkerFactory
    @Inject
    constructor(
        private val deps: UploadDeps,
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? =
            if (workerClassName == UploadWorker::class.java.name) {
                UploadWorker(appContext, workerParameters, deps)
            } else {
                null
            }
    }
