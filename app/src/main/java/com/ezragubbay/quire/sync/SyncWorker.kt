package com.ezragubbay.quire.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

class SyncDeps(
    val runner: SyncRunner,
)

/**
 * Background metadata sync. Silent (no notification). Transient failures return [Result.retry] so WorkManager
 * backs off exponentially, up to [MAX_ATTEMPTS]; a `401` fails at once (no retry loop). For periodic work a
 * `failure` only ends this run; the next period still fires.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val deps: SyncDeps,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        when (deps.runner.run()) {
            SyncOutcome.Success, SyncOutcome.Skipped -> Result.success()
            SyncOutcome.Unauthorized -> Result.failure()
            is SyncOutcome.Partial -> if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }

    companion object {
        const val MAX_ATTEMPTS = 3
    }
}

/** Builds [SyncWorker]; chained with the download factory in `QuireApplication`. Other classes fall through. */
class SyncWorkerFactory(
    private val deps: SyncDeps,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name) {
            SyncWorker(appContext, workerParameters, deps)
        } else {
            null
        }
}
