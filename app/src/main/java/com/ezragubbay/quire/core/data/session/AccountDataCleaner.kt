package com.ezragubbay.quire.core.data.session

import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.download.DownloadRepository
import com.ezragubbay.quire.feature.viewer.ViewerCache
import com.ezragubbay.quire.sync.OfflineSettingsStore
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
    override fun observeDownloadBytes(): Flow<Long> = downloads.observeStorageBytes()

    override suspend fun clear(removeDownloads: Boolean) {
        downloads.removeUnfinished()
        db.accountCacheDao().clearCourseCache()
        withContext(Dispatchers.IO) { viewerCache.clear() }
        if (removeDownloads) {
            downloads.purgeAll()
            offline.clearPins()
        }
    }
}
