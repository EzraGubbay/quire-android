package com.ezragubbay.quire.core.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** A newer release the user has not snoozed. [version] is bare (`1.2.0`, no `v`). */
data class UpdatePrompt(
    val version: String,
    val releaseUrl: String,
)

/**
 * Decides when to ask GitHub (at most once per [CHECK_INTERVAL_MS]) and whether the cached latest release is worth
 * a prompt. The latest release is cached, so the prompt survives process death with no network call.
 */
class UpdateChecker(
    private val source: LatestReleaseSource,
    private val store: UpdateStore,
    private val currentVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val prompt: Flow<UpdatePrompt?> =
        store.state.map(::promptFor).distinctUntilChanged()

    /** Fetches if the window has elapsed. Never throws (except cancellation). */
    suspend fun checkIfDue() {
        try {
            val now = clock()
            if (isDue(store.state.first().lastCheckMs, now)) {
                when (val result = source.fetch()) {
                    is FetchResult.Found -> store.recordCheck(now, result.release)
                    FetchResult.NoRelease -> store.recordCheck(now, null)
                    FetchResult.HttpError -> store.recordAttempt(now)
                    // Offline: record nothing so the next resume retries as soon as the network is back.
                    FetchResult.NetworkError -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("SwallowedException") _: IOException,
        ) {
            // A DataStore failure must never reach the UI.
        }
    }

    suspend fun snooze(version: String) {
        try {
            store.snooze(version)
        } catch (
            @Suppress("SwallowedException") _: IOException,
        ) {
            // Not persisted; the dialog is still hidden for this process.
        }
    }

    private fun isDue(
        last: Long?,
        now: Long,
    ): Boolean = last == null || now < last || now - last >= CHECK_INTERVAL_MS

    private fun promptFor(state: UpdateState): UpdatePrompt? {
        val latest = state.latestTag?.let(AppVersion::parse)
        val current = AppVersion.parse(currentVersion)
        val newer = latest != null && current != null && !latest.isPrerelease && latest > current
        return if (newer && state.snoozedVersion != latest.toString()) {
            UpdatePrompt(latest.toString(), state.latestUrl ?: "${GitHubReleaseSource.RELEASES_PAGE}/latest")
        } else {
            null
        }
    }

    companion object {
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
