package com.ezragubbay.quire.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.core.data.session.SessionRepository
import com.ezragubbay.quire.core.data.session.SessionState
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.QuireClient
import com.ezragubbay.quire.core.network.SessionEvents
import com.ezragubbay.quire.core.network.TokenCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val REVOKE_TIMEOUT_MS = 5_000L

/**
 * Root routing state and session lifecycle. [signedIn] is `null` until the persisted session is read (the splash
 * waits), then signed-in or not. The session ends three ways: a `401` anywhere ([SessionEvents.expired]), a `401`
 * from the launch validation ([validateSession]) — both flag [sessionExpired] so Pair explains why — or a
 * deliberate [signOut] (no message).
 */
@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        events: SessionEvents,
        private val tokenCache: TokenCache,
        private val client: QuireClient,
    ) : ViewModel() {
        private val _signedIn = MutableStateFlow<Boolean?>(null)
        val signedIn: StateFlow<Boolean?> = _signedIn.asStateFlow()

        private val _sessionExpired = MutableStateFlow(false)

        /** True after the session was ended by a `401`; the Pair screen shows "signed out, pair again" once. */
        val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

        /** Identity of the signed-in device, for Settings. */
        val session: StateFlow<SessionState> = sessions.state

        @Volatile
        private var signingOut = false

        internal var validation: Job? = null
            private set

        init {
            viewModelScope.launch {
                val signedIn = sessions.current() is SessionState.SignedIn
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

        /** Revokes the device token (best effort), clears the session and routes to Pair without a message. */
        fun signOut(): Job =
            viewModelScope.launch {
                signingOut = true
                try {
                    validation?.cancel()
                    withTimeoutOrNull(REVOKE_TIMEOUT_MS) { client.revokeToken() }
                    sessions.signOut()
                    tokenCache.invalidate()
                    _sessionExpired.value = false
                    _signedIn.value = false
                } finally {
                    signingOut = false
                }
            }

        /** Called after pairing stored a session. */
        fun onSignedIn() {
            _sessionExpired.value = false
            _signedIn.value = true
        }

        /** The Pair screen has taken the expiry message. */
        fun onExpiredNoticeShown() {
            _sessionExpired.value = false
        }

        private suspend fun expire() {
            if (signingOut || _signedIn.value != true) return
            sessions.signOut()
            tokenCache.invalidate()
            _sessionExpired.value = true
            _signedIn.value = false
        }
    }
