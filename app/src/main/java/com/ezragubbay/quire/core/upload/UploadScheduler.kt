package com.ezragubbay.quire.core.upload

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
interface UploadScheduler {
    fun enqueue(uploadId: String)

    fun cancel(uploadId: String)

    fun cancelAll()
}

class WorkManagerUploadScheduler(
    private val context: Context,
) : UploadScheduler {
    private val work get() = WorkManager.getInstance(context)

    override fun enqueue(uploadId: String) {
        val request =
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf(UploadWorker.KEY_UPLOAD_ID to uploadId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
        // KEEP: a second tap while one is queued/running is a no-op; a finished or failed one is replaced.
        work.enqueueUniqueWork(uniqueName(uploadId), ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(uploadId: String) {
        work.cancelUniqueWork(uniqueName(uploadId))
    }

    override fun cancelAll() {
        work.cancelAllWorkByTag(TAG)
    }

    companion object {
        const val TAG = "upload"
        private const val BACKOFF_SECONDS = 10L

        fun uniqueName(uploadId: String) = "upload-$uploadId"
    }
}
