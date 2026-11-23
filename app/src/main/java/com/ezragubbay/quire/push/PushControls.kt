package com.ezragubbay.quire.push

import com.ezragubbay.quire.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What Settings → Notifications drives (QMOB-E8-S5, ADR 0003 §4.6). Everything runs on the application scope, so
 * leaving Settings mid-way never cancels a write, a registration or a test push's re-registration.
 */
interface PushControls {
    /** False when this build cannot receive pushes at all (no `google-services.json`): Settings says so. */
    val available: Boolean

    /** Stores a category switch, then re-registers once the user stops flipping switches ([onCategoriesChanged]). */
    fun setCategory(
        category: PushCategory,
        enabled: Boolean,
    )

    /** The registered categories may have changed (a switch, or the system permission): re-register, debounced. */
    fun onCategoriesChanged()

    /** `POST /push/test`; see [PushRegistrar.sendTest]. */
    suspend fun sendTest(): TestPushResult
}

/**
 * [PushControls] over [PushRegistrar] and [NotificationPreferencesStore].
 *
 * [onCategoriesChanged] is a debounced [PushRegistrar.requestSync]: each call restarts a [CATEGORY_SYNC_DEBOUNCE_MS]
 * wait, so a burst of flips sends one `PUT /push/registration`. Only the wait is cancelled, never a `PUT` in flight;
 * a flip during a `PUT` queues another one behind it (the registrar serialises syncs), which reads the latest switches.
 */
@Singleton
class RegistrarPushControls
    @Inject
    constructor(
        private val registrar: PushRegistrar,
        private val preferences: NotificationPreferencesStore,
        private val tokens: FcmTokenSource,
        @ApplicationScope private val scope: CoroutineScope,
    ) : PushControls {
        private val pending = AtomicReference<Job?>()
        private val writes = Mutex()

        override val available: Boolean get() = tokens.available

        override fun setCategory(
            category: PushCategory,
            enabled: Boolean,
        ) {
            // Undispatched: the lock is requested before this returns, so flips are stored in the order they were made.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                writes.withLock { preferences.setEnabled(category, enabled) }
                onCategoriesChanged()
            }
        }

        override fun onCategoriesChanged() {
            if (!tokens.available) return
            val next =
                scope.launch(start = CoroutineStart.LAZY) {
                    delay(CATEGORY_SYNC_DEBOUNCE_MS)
                    registrar.requestSync()
                }
            pending.getAndSet(next)?.cancel()
            next.start()
        }

        /** On the application scope: a caller that goes away mid-`unregistered` cannot cancel the re-registration. */
        override suspend fun sendTest(): TestPushResult = scope.async { registrar.sendTest() }.await()

        companion object {
            /** How long the switches must stay still before their categories are sent. */
            const val CATEGORY_SYNC_DEBOUNCE_MS = 750L
        }
    }
