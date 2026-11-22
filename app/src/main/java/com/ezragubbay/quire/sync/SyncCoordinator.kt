package com.ezragubbay.quire.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the WorkManager schedule in step with [SyncSettings] and queues an app-start sync when the last success
 * is stale. [start] is idempotent; call it from the launch activity.
 */
class SyncCoordinator(
    private val store: SyncSettingsStore,
    private val scheduler: SyncScheduler,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val first = store.current()
            if (first.periodic && SyncPolicy.isStale(first.lastSuccessMillis, clock())) {
                scheduler.syncSoon(first.wifiOnly)
            }
            // Reschedule only when something that shapes the work changes (not on every last-success stamp).
            store.settings
                .distinctUntilChangedBy { Triple(it.enabled, it.interval, it.wifiOnly) }
                .collect { scheduler.apply(it) }
        }
    }
}
