package com.ezragubbay.quire.core.data.session

/**
 * Whether this install is paired with a Quire server. Deliberately carries no token: read it on demand via
 * [SessionRepository.token] so it cannot leak through UI state, logs or saved instance state.
 */
sealed interface SessionState {
    data object SignedOut : SessionState

    data class SignedIn(
        val serverUrl: String,
        val login: String,
        val deviceName: String,
    ) : SessionState
}
