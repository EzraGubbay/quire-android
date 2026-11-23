package com.ezragubbay.quire.push

import com.ezragubbay.quire.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** What Settings → Notifications drives on the registrar (QMOB-E8-S5, ADR 0003 §4.6). */
interface PushControls {
    /** False when this build cannot receive pushes at all (no `google-services.json`): Settings says so. */
    val available: Boolean

    /** A category switch changed: re-register with the current categories once the user stops flipping switches. */
    fun onCategoriesChanged()

    /** `POST /push/test`; see [PushRegistrar.sendTest]. */
    suspend fun sendTest(): TestPushResult
}

/**
 * [PushControls] over [PushRegistrar]. [onCategoriesChanged] is a debounced [PushRegistrar.requestSync]: each call
 * restarts a [CATEGORY_SYNC_DEBOUNCE_MS] wait, so a burst of flips sends one `PUT /push/registration` with the final
 * categories. Only the wait is cancelled, never a `PUT` in flight. It runs on the application scope (a singleton), so
 * leaving Settings straight after a flip still registers it.
 */
@Singleton
class RegistrarPushControls
    @Inject
    constructor(
        private val registrar: PushRegistrar,
        private val tokens: FcmTokenSource,
        @ApplicationScope private val scope: CoroutineScope,
    ) : PushControls {
        private val pending = AtomicReference<Job?>()

        override val available: Boolean get() = tokens.available

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

        override suspend fun sendTest(): TestPushResult = registrar.sendTest()

        companion object {
            /** How long the switches must stay still before their categories are sent. */
            const val CATEGORY_SYNC_DEBOUNCE_MS = 750L
        }
    }
