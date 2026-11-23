package com.ezragubbay.quire.push

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Retries a push registration (ADR 0003 §4.2): runs [PushRegistrar.sync] with a network constraint and exponential
 * backoff. Also what `onNewToken` enqueues, since the messaging service has no UI scope.
 */
class PushRegistrationWorker(
    context: Context,
    params: WorkerParameters,
    private val registrar: () -> PushRegistrar,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        when (registrar().sync()) {
            RegistrationOutcome.Registered, RegistrationOutcome.Skipped -> Result.success()
            RegistrationOutcome.Unauthorized, RegistrationOutcome.Failed -> Result.failure()
            RegistrationOutcome.Retry -> if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }

    companion object {
        const val UNIQUE_NAME = "push-registration"
        const val MAX_ATTEMPTS = 8
    }
}

/** Builds [PushRegistrationWorker]; chained with the other factories in `QuireApplication`. */
class PushRegistrationWorkerFactory(
    private val registrar: () -> PushRegistrar,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == PushRegistrationWorker::class.java.name) {
            PushRegistrationWorker(appContext, workerParameters, registrar)
        } else {
            null
        }
}

/** [PushRegistrationScheduler] over WorkManager: one-time unique work, replaced by the newest request. */
class WorkManagerPushRegistrationScheduler(
    private val context: Context,
) : PushRegistrationScheduler {
    /** Null when WorkManager is not initialised (instrumented tests under the Hilt test application). */
    private val work: WorkManager?
        get() =
            try {
                WorkManager.getInstance(context)
            } catch (_: IllegalStateException) {
                null
            }

    override fun enqueue() {
        val request =
            OneTimeWorkRequestBuilder<PushRegistrationWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        work?.enqueueUniqueWork(PushRegistrationWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        work?.cancelUniqueWork(PushRegistrationWorker.UNIQUE_NAME)
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L
    }
}
