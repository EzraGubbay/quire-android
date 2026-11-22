package com.ezragubbay.quire.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Seam over WorkManager for the metadata sync schedule. */
interface SyncScheduler {
    /** Makes the periodic work match [settings]: updated in place when on, cancelled when off. Idempotent. */
    fun apply(settings: SyncSettings)

    /** Queues one unique sync for as soon as the network allows (the app-start sync). A pending one is kept. */
    fun syncSoon(wifiOnly: Boolean)

    companion object {
        const val PERIODIC_NAME = "quire-sync"
        const val STARTUP_NAME = "quire-sync-startup"
    }
}

object SyncPolicy {
    /** The app-start sync runs only when the last successful sync is older than this (or there never was one). */
    const val STALE_MS = 60 * 60_000L

    fun isStale(
        lastSuccessMillis: Long?,
        nowMillis: Long,
    ): Boolean = lastSuccessMillis == null || nowMillis - lastSuccessMillis > STALE_MS
}

class WorkManagerSyncScheduler(
    private val context: Context,
) : SyncScheduler {
    /**
     * Null when WorkManager is not initialised (instrumented journeys that run under the Hilt test application and
     * do not install a test WorkManager): scheduling is then a no-op instead of crashing the launch activity.
     */
    private val work: WorkManager?
        get() =
            try {
                WorkManager.getInstance(context)
            } catch (_: IllegalStateException) {
                null
            }

    override fun apply(settings: SyncSettings) {
        if (!settings.periodic) {
            work?.cancelUniqueWork(SyncScheduler.PERIODIC_NAME)
            return
        }
        val request =
            PeriodicWorkRequestBuilder<SyncWorker>(settings.interval.hours, TimeUnit.HOURS)
                .setConstraints(constraints(settings.wifiOnly, batteryNotLow = true))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        // UPDATE: a changed interval/constraint is applied in place (same work id, next run time kept); same = no-op.
        work?.enqueueUniquePeriodicWork(SyncScheduler.PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun syncSoon(wifiOnly: Boolean) {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints(wifiOnly, batteryNotLow = false))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        work?.enqueueUniqueWork(SyncScheduler.STARTUP_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun constraints(
        wifiOnly: Boolean,
        batteryNotLow: Boolean,
    ) = Constraints
        .Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(batteryNotLow)
        .build()

    private companion object {
        const val BACKOFF_SECONDS = 30L
    }
}
