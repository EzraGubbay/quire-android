package com.ezragubbay.quire.core.data.session

import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.download.DownloadRepository
import com.ezragubbay.quire.feature.viewer.ViewerCache
import com.ezragubbay.quire.sync.OfflineSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Wipes the signed-out account's local data (#92, #101). */
interface AccountDataCleaner {
    /** Bytes held by downloads right now; non-zero means sign-out should offer to remove them. */
    fun observeDownloadBytes(): Flow<Long>

    /**
     * Always: cancels download work, drops unfinished download rows and their partial files, empties the Room course
     * cache and the viewer cache. With [removeDownloads]: also deletes finished downloads (rows, the whole downloads
     * directory including orphans) and the offline pins and batches, so the next sign-in does not re-download.
     * Without it, finished downloads and pins stay.
     */
    suspend fun clear(removeDownloads: Boolean)
}

class LocalAccountDataCleaner(
    private val db: QuireDatabase,
    private val downloads: DownloadRepository,
    private val viewerCache: ViewerCache,
    private val offline: OfflineSettingsStore,
) : AccountDataCleaner {
    override fun observeDownloadBytes(): Flow<Long> = downloads.observeDoneBytes()

    override suspend fun clear(removeDownloads: Boolean) {
        // Each step runs even if an earlier one failed (a failed cache wipe must not leave the downloads behind
        // when the user asked to remove them); the first failure is rethrown at the end.
        val steps = mutableListOf<suspend () -> Unit>()
        steps += { downloads.removeUnfinished() }
        steps += { db.accountCacheDao().clearCourseCache() }
        steps += { withContext(Dispatchers.IO) { viewerCache.clear() } }
        if (removeDownloads) {
            steps += { downloads.purgeAll() }
            steps += { offline.clearPins() }
        }
        var first: Throwable? = null
        for (step in steps) {
            try {
                step()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                if (first == null) first = e
            }
        }
        first?.let { throw it }
    }
}
