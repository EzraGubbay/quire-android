package com.ezragubbay.quire.push

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.ezragubbay.quire.sync.SyncSettingsStore
import com.ezragubbay.quire.sync.SyncWorker
import java.util.concurrent.TimeUnit

/** The metadata syncs a push starts (ADR 0003 §2, §4.3). */
interface PushSyncTrigger {
    /** `files.added`: an expedited one-time sync, so pinned courses auto-download the new file. */
    suspend fun onFilesAdded()

    /** FCM dropped pending messages (`onDeletedMessages`): a one-time sync catches up. */
    suspend fun onMessagesDeleted()
}

/**
 * Enqueues the E7 [SyncWorker] as unique one-time work [UNIQUE_NAME]. A push that arrives while a sync is running is
 * appended after it, so the new file is not missed by a pass that already fetched its course. Respects the Sync
 * settings: nothing when background sync is off, unmetered networks only when "Wi-Fi only" is on.
 */
class WorkManagerPushSyncTrigger(
    private val context: Context,
    private val settings: SyncSettingsStore,
) : PushSyncTrigger {
    override suspend fun onFilesAdded() = enqueue(expedited = true)

    override suspend fun onMessagesDeleted() = enqueue(expedited = false)

    private suspend fun enqueue(expedited: Boolean) {
        val current = settings.current()
        if (!current.enabled) return
        val work =
            try {
                WorkManager.getInstance(context)
            } catch (_: IllegalStateException) {
                // Not initialised (instrumented tests under the Hilt test application): nothing to schedule.
                return
            }
        work.enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request(expedited = expedited && supportsExpedited(), wifiOnly = current.wifiOnly),
        )
    }

    companion object {
        const val UNIQUE_NAME = "quire-sync-push"
        private const val BACKOFF_SECONDS = 30L

        /**
         * Expedited work below Android 12 runs as a foreground service and needs the worker to supply a notification,
         * which [SyncWorker] does not; there it is enqueued as ordinary one-time work.
         */
        fun supportsExpedited(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= Build.VERSION_CODES.S

        /** The sync request; when [expedited], out of quota it degrades to ordinary work instead of being dropped. */
        fun request(
            expedited: Boolean,
            wifiOnly: Boolean,
        ): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .apply { if (expedited) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
                .build()
    }
}
