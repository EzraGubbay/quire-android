package com.ezragubbay.quire.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.core.data.session.AccountDataCleaner
import com.ezragubbay.quire.core.data.session.SessionRepository
import com.ezragubbay.quire.core.data.session.SessionState
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.QuireClient
import com.ezragubbay.quire.core.network.SessionEvents
import com.ezragubbay.quire.core.network.TokenCache
import com.ezragubbay.quire.core.upload.UploadQueue
import com.ezragubbay.quire.push.PushSession
import com.ezragubbay.quire.push.PushTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val REVOKE_TIMEOUT_MS = 5_000L
private const val STOP_TIMEOUT_MS = 5_000L

/** The push-registration delete's own share of [REVOKE_TIMEOUT_MS], so the token revoke always gets the rest. */
private const val PUSH_DELETE_TIMEOUT_MS = 2_000L

/**
 * Root routing state and session lifecycle. [signedIn] is `null` until the persisted session is read (the splash
 * waits), then signed-in or not. The session ends three ways: a `401` anywhere ([SessionEvents.expired]), a `401`
 * from the launch validation ([validateSession]) — both flag [sessionExpired] so Pair explains why — or a
 * deliberate [signOut] (no message). Push follows the session (ADR 0003 §4.2): pairing registers, sign-out deletes the
 * registration before revoking the token, and any end of session drops the local FCM token. A notification tap
 * ([openFromPush]) waits in [pendingPushTarget] until the signed-in shell consumes it (ADR 0003 §4.5).
 */
@Suppress("LongParameterList") // one collaborator per session-lifecycle concern, all injected
@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        events: SessionEvents,
        private val tokenCache: TokenCache,
        private val client: QuireClient,
        private val push: PushSession,
        private val cleaner: AccountDataCleaner,
        private val uploads: UploadQueue,
    ) : ViewModel() {
        private val _signedIn = MutableStateFlow<Boolean?>(null)
        val signedIn: StateFlow<Boolean?> = _signedIn.asStateFlow()

        private val _sessionExpired = MutableStateFlow(false)

        /** True after the session was ended by a `401`; the Pair screen shows "signed out, pair again" once. */
        val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

        /** Bytes held by downloads; Settings offers to remove them on sign-out only when this is non-zero. */
        val downloadBytes: StateFlow<Long> =
            cleaner.observeDownloadBytes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0L)

        /** Identity of the signed-in device, for Settings. */
        val session: StateFlow<SessionState> = sessions.state

        private val _pendingPushTarget = MutableStateFlow<PushTarget?>(null)

        /**
         * The tapped notification's target, until the shell navigates to it and calls [onPushTargetConsumed]. Held
         * while [signedIn] is `null` (the shell is not composed yet); dropped whenever the session is signed out.
         * Memory only: a target not yet opened is lost on process death (the restored activity never replays the
         * tap), so the app then reopens where it was.
         */
        val pendingPushTarget: StateFlow<PushTarget?> = _pendingPushTarget.asStateFlow()

        @Volatile
        private var signingOut = false

        internal var validation: Job? = null
            private set

        init {
            viewModelScope.launch {
                val signedIn = sessions.current() is SessionState.SignedIn
                if (!signedIn) _pendingPushTarget.value = null
                _signedIn.value = signedIn
                if (signedIn) validateSession()
            }
            viewModelScope.launch { events.expired.collect { expire() } }
        }

        /**
         * Checks the stored session once with `GET /me`; only a `401` ends it (offline or server errors keep the
         * user signed in). Safe to call repeatedly: it does nothing while signed out or while a check is running.
         */
        fun validateSession() {
            if (_signedIn.value != true || validation?.isActive == true) return
            validation =
                viewModelScope.launch {
                    if (client.me() is ApiResult.Failure.Unauthorized) expire()
                }
        }

        /**
         * Deletes the push registration and revokes the device token (best effort, one timeout for both, in that
         * order), clears the session, drops the local FCM token, wipes local account data ([AccountDataCleaner]:
         * course cache, viewer cache and unfinished downloads always; finished downloads and pins only when
         * [removeDownloads]) and routes to Pair without a message. A cleanup failure never blocks sign-out. The
         * upload queue is cleared too. A `401` expiry ([expire]) wipes nothing else (the user did not choose to sign
         * out and usually re-pairs) but also drops the upload queue, which could not be sent without a session.
         */
        fun signOut(removeDownloads: Boolean = false): Job =
            viewModelScope.launch {
                signingOut = true
                try {
                    validation?.cancel()
                    withTimeoutOrNull(REVOKE_TIMEOUT_MS) {
                        withTimeoutOrNull(PUSH_DELETE_TIMEOUT_MS) { push.unregisterRemote() }
                        client.revokeToken()
                    }
                    sessions.signOut()
                    clearUploads()
                    tokenCache.invalidate()
                    push.onSignedOut()
                    try {
                        cleaner.clear(removeDownloads)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") _: Exception,
                    ) {
                        // Best effort: leftover cache must never keep the user from signing out.
                    }
                    _sessionExpired.value = false
                    _pendingPushTarget.value = null
                    _signedIn.value = false
                } finally {
                    signingOut = false
                }
            }

        /** Called after pairing stored a session. */
        fun onSignedIn() {
            _sessionExpired.value = false
            _signedIn.value = true
            push.onSignedIn()
        }

        /**
         * A notification was tapped (cold start or while running). A newer tap replaces an unconsumed one; while
         * signed out the tap just opens the app (Pair), so the target is dropped.
         */
        fun openFromPush(target: PushTarget) {
            if (_signedIn.value == false) return
            _pendingPushTarget.value = target
        }

        /** The shell navigated to [target]; a newer tap that arrived meanwhile stays pending. */
        fun onPushTargetConsumed(target: PushTarget) {
            _pendingPushTarget.compareAndSet(target, null)
        }

        /** The Pair screen has taken the expiry message. */
        fun onExpiredNoticeShown() {
            _sessionExpired.value = false
        }

        /** Upload cleanup must never keep the user signed in: a failure here is swallowed. */
        private suspend fun clearUploads() {
            try {
                uploads.clearAll()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort: queued rows are also dropped with the app data on uninstall.
            }
        }

        private suspend fun expire() {
            if (signingOut || _signedIn.value != true) return
            sessions.signOut()
            clearUploads()
            tokenCache.invalidate()
            // The server already deleted the registration with the token; only the local half remains.
            push.onSignedOut()
            _sessionExpired.value = true
            _pendingPushTarget.value = null
            _signedIn.value = false
        }
    }
