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

/**
 * A persisted "the current FCM token is dead" mark. It is set when the server reports `unregistered`, and cleared only
 * once the token was deleted at FCM, so no later sync (in this process or after a restart) can PUT the dead token.
 */
interface StaleTokenFlag {
    suspend fun isSet(): Boolean

    suspend fun set(stale: Boolean)
}

/** Process-local [StaleTokenFlag] (tests; the app persists it in DataStore). */
class InMemoryStaleTokenFlag : StaleTokenFlag {
    @Volatile
    private var stale = false

    override suspend fun isSet() = stale

    override suspend fun set(stale: Boolean) {
        this.stale = stale
    }
}

/** This app install as the registration sees it: its version, whether a session exists, its stale-token mark. */
class PushDevice(
    val appVersion: String,
    val staleToken: StaleTokenFlag = InMemoryStaleTokenFlag(),
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

        /**
         * Registers this device's current token and categories. Idempotent; serialised. While the token is marked
         * stale it is deleted first, and nothing is sent until that delete succeeds.
         */
        suspend fun sync(): RegistrationOutcome =
            mutex.withLock {
                if (!tokens.available || !device.isSignedIn()) return@withLock RegistrationOutcome.Skipped
                if (device.staleToken.isSet()) {
                    if (!deleteToken()) return@withLock RegistrationOutcome.Retry
                    device.staleToken.set(false)
                }
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

        /**
         * The local half of sign-out: invalidate the token at FCM, then disable auto-init. Serialised with [sync], so
         * an in-flight registration cannot re-enable auto-init after it.
         */
        suspend fun clearLocal() {
            if (!tokens.available) return
            mutex.withLock {
                deleteToken()
                tokens.setAutoInitEnabled(false)
            }
        }

        /**
         * Sends `POST /push/test` (the Settings control is QMOB-E8-S5). On `outcome: unregistered` the server
         * already dropped the registration because FCM rejected the token. The token is marked stale (persisted), so
         * this and every later [sync] deletes it at FCM before fetching a fresh one: the dead token is never sent
         * again, even when the delete has to wait for the retry worker.
         */
        suspend fun sendTest(): TestPushResult {
            if (!tokens.available) return TestPushResult.NotConfigured
            val result = client.sendTest()
            if (result == TestPushResult.Unregistered) {
                device.staleToken.set(true)
                if (sync() == RegistrationOutcome.Retry) retry.enqueue()
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
