package com.ezragubbay.quire.core.data.course

import com.ezragubbay.quire.core.network.ApiResult

/** Progress of the network refresh behind a cached read. Cached data is never cleared by a failure. */
sealed interface SyncState {
    /** Nothing in flight and nothing known yet (before the first refresh is scheduled). */
    data object Idle : SyncState

    data object Refreshing : SyncState

    data object Synced : SyncState

    data class Failed(
        val failure: ApiResult.Failure,
    ) : SyncState {
        /** True when the server was unreachable (no signal, timeout), as opposed to it answering with an error. */
        val isOffline: Boolean get() = failure is ApiResult.Failure.Network
    }
}

/** What a screen observes: the cached [data] (served immediately) plus the [state] of the refresh behind it. */
data class Loaded<out T>(
    val data: T,
    val state: SyncState,
)

/** Show an "offline" banner over cached content: the network failed but there is something cached to show. */
val Loaded<List<*>>.showsOfflineBanner: Boolean
    get() = (state as? SyncState.Failed)?.isOffline == true && data.isNotEmpty()

/** Show a full error state: the refresh failed and the cache has nothing to show. */
val Loaded<List<*>>.showsError: Boolean
    get() = state is SyncState.Failed && data.isEmpty()

/** Show a full-screen loading state: nothing cached yet and the first refresh is still running. */
val Loaded<List<*>>.showsLoading: Boolean
    get() = data.isEmpty() && (state == SyncState.Refreshing || state == SyncState.Idle)
