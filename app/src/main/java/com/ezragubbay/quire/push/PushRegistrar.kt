package com.ezragubbay.quire.push

import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Re-runs a failed registration later (WorkManager unique work `push-registration`). */
interface PushRegistrationScheduler {
    fun enqueue()

    fun cancel()
}

/** Outcome of one [PushRegistrar.sync]. */
enum class RegistrationOutcome {
    /** The server stored the token and categories. */
    Registered,

    /** Nothing to do: push not configured in this build, or signed out. */
    Skipped,

    /** A transient failure (offline, 5xx, no token yet): worth a backed-off retry. */
    Retry,

    /** The session is gone (`401`); the network layer already raised session expiry. */
    Unauthorized,

    /** A permanent failure (`400`): retrying the same request cannot help. */
    Failed,
}

/** What the registration says about this app install: its version, and whether a session exists. */
class PushDevice(
    val appVersion: String,
    val isSignedIn: suspend () -> Boolean,
)

/**
 * The FCM token lifecycle (ADR 0003 §4.2). [sync] always sends `PUT /push/registration` (no fingerprint skip), so a
 * registration the server pruned or lost comes back on the next app start without any client bookkeeping. Failed
 * syncs are retried by [PushRegistrationScheduler]. The token is never cached here: every sync asks [tokens].
 */
@Singleton
class PushRegistrar
    @Inject
    constructor(
        private val tokens: FcmTokenSource,
        private val client: PushClient,
        private val device: PushDevice,
        private val categories: PushCategoryPolicy,
        private val retry: PushRegistrationScheduler,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val started = AtomicBoolean(false)
        private val mutex = Mutex()

        @Volatile
        private var lastCategories: List<PushCategory>? = null

        /** Registers this device's current token and categories. Idempotent; serialised. */
        suspend fun sync(): RegistrationOutcome =
            mutex.withLock {
                if (!tokens.available || !device.isSignedIn()) return@withLock RegistrationOutcome.Skipped
                tokens.setAutoInitEnabled(true)
                val token = firebaseCall("token") { tokens.token() } ?: return@withLock RegistrationOutcome.Retry
                val wanted = categories.current()
                val body =
                    PushRegistrationRequest(
                        fcmToken = token,
                        platform = PushRegistrationRequest.PLATFORM_ANDROID,
                        appVersion = device.appVersion,
                        categories = wanted.map { it.wire },
                    )
                when (client.register(body)) {
                    is ApiResult.Success -> {
                        lastCategories = wanted
                        RegistrationOutcome.Registered
                    }
                    is ApiResult.Failure.Unauthorized -> RegistrationOutcome.Unauthorized
                    is ApiResult.Failure.InvalidRequest -> RegistrationOutcome.Failed
                    is ApiResult.Failure -> RegistrationOutcome.Retry
                }
            }

        /** Runs [sync] in the background; a transient failure is handed to the retry worker. */
        fun requestSync() {
            scope.launch {
                if (sync() == RegistrationOutcome.Retry) retry.enqueue()
            }
        }

        /**
         * Re-registers when the categories this device would send changed since the last successful sync, for example
         * after notifications were allowed or turned off in system settings. Cheap: call it on resume.
         */
        fun refreshIfChanged() {
            val last = lastCategories ?: return
            scope.launch {
                if (categories.current() != last) requestSync()
            }
        }

        /** Once per process: register on every app start. */
        fun onAppStart() {
            if (started.compareAndSet(false, true)) requestSync()
        }

        /** Sign-out, before `DELETE /auth/token`: best-effort `DELETE /push/registration`. */
        suspend fun unregisterRemote() {
            if (!tokens.available) return
            client.unregister()
        }

        /** Sign-out (after the revoke) or session expiry: [clearLocal] in the background; no more retries. */
        fun onSignedOut() {
            if (!tokens.available) return
            lastCategories = null
            retry.cancel()
            scope.launch { clearLocal() }
        }

        /** The local half of sign-out: invalidate the token at FCM, then disable auto-init. */
        suspend fun clearLocal() {
            if (!tokens.available) return
            deleteToken()
            tokens.setAutoInitEnabled(false)
        }

        /**
         * Sends `POST /push/test` (the Settings control is QMOB-E8-S5). On `outcome: unregistered` the server
         * already dropped the registration because FCM rejected the token, so the dead token is deleted first and a
         * fresh one is fetched and registered: the dead token is never sent again.
         */
        suspend fun sendTest(): TestPushResult {
            if (!tokens.available) return TestPushResult.NotConfigured
            val result = client.sendTest()
            if (result == TestPushResult.Unregistered) {
                // Without a successful delete the next token() could hand back the dead token: leave it to the worker.
                if (!deleteToken() || sync() == RegistrationOutcome.Retry) retry.enqueue()
            }
            return result
        }

        private suspend fun deleteToken(): Boolean = firebaseCall("delete") { tokens.delete() } != null

        /** Runs a Firebase call. On failure it logs only the exception class (a message can quote the token): null. */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun <T : Any> firebaseCall(
            what: String,
            call: suspend () -> T?,
        ): T? =
            try {
                call()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PushLog.warn("FCM $what failed: ${e.javaClass.simpleName}")
                null
            }
    }
