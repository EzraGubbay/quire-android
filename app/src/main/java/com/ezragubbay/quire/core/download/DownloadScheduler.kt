package com.ezragubbay.quire.core.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Seam over WorkManager so the repository is unit-testable without a worker. */
interface DownloadScheduler {
    fun enqueue(
        courseSlug: String,
        fileId: String,
        format: String,
        wifiOnly: Boolean,
    )

    fun cancel(fileId: String)

    fun cancelAll()
}

class WorkManagerDownloadScheduler(
    private val context: Context,
) : DownloadScheduler {
    private val work get() = WorkManager.getInstance(context)

    override fun enqueue(
        courseSlug: String,
        fileId: String,
        format: String,
        wifiOnly: Boolean,
    ) {
        val request =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    workDataOf(
                        DownloadWorker.KEY_FILE_ID to fileId,
                        DownloadWorker.KEY_COURSE to courseSlug,
                        DownloadWorker.KEY_FORMAT to format,
                    ),
                ).setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
        // KEEP: a second tap while one is queued/running is a no-op; a finished or failed one is replaced.
        work.enqueueUniqueWork(uniqueName(fileId), ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(fileId: String) {
        work.cancelUniqueWork(uniqueName(fileId))
    }

    override fun cancelAll() {
        work.cancelAllWorkByTag(TAG)
    }

    companion object {
        const val TAG = "download"
        private const val BACKOFF_SECONDS = 10L

        fun uniqueName(fileId: String) = "download-$fileId"
    }
}
