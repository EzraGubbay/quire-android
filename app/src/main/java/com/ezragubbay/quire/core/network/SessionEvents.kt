package com.ezragubbay.quire.core.network

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Emitted when an authenticated call got `401`: the stored token is dead and the app must re-pair. */
data object SessionExpired

class SessionEvents {
    private val _expired =
        MutableSharedFlow<SessionExpired>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val expired: SharedFlow<SessionExpired> = _expired

    internal fun emitExpired() {
        _expired.tryEmit(SessionExpired)
    }
}
