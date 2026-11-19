package com.ezragubbay.quire.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.core.data.session.SessionRepository
import com.ezragubbay.quire.core.data.session.SessionState
import com.ezragubbay.quire.core.network.SessionEvents
import com.ezragubbay.quire.core.network.TokenCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Root routing state: `null` until the persisted session is read (the splash waits), then signed-in or not.
 * A `401` anywhere ([SessionEvents.expired]) clears the session and flips this to signed-out.
 */
@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        events: SessionEvents,
        private val tokenCache: TokenCache,
    ) : ViewModel() {
        private val _signedIn = MutableStateFlow<Boolean?>(null)
        val signedIn: StateFlow<Boolean?> = _signedIn.asStateFlow()

        init {
            viewModelScope.launch { _signedIn.value = sessions.current() is SessionState.SignedIn }
            viewModelScope.launch {
                events.expired.collect {
                    sessions.signOut()
                    tokenCache.invalidate()
                    _signedIn.value = false
                }
            }
        }

        /** Called after pairing stored a session. */
        fun onSignedIn() {
            _signedIn.value = true
        }
    }
