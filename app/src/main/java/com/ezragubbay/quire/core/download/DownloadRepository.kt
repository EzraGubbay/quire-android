package com.ezragubbay.quire.core.download

import com.ezragubbay.quire.core.database.DownloadDao
import com.ezragubbay.quire.core.database.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Public download API (E5). Room is the source of truth, so every observer survives process death; WorkManager only
 * does the transfer. Inject this in the viewer (#38) and the Downloads tab (#39).
 */
@Suppress("TooManyFunctions")
class DownloadRepository(
    private val dao: DownloadDao,
    private val scheduler: DownloadScheduler,
    private val paths: DownloadPaths,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Queues a download (no-op if already Done or running); a Failed one restarts, resuming from its `.part`. */
    suspend fun enqueue(
        courseSlug: String,
        fileId: String,
        format: String,
        wifiOnly: Boolean = false,
    ) {
        val existing = dao.get(fileId)
        if (existing?.state == DownloadEntity.DONE && existing.format == format) return
        val prior = existing?.takeIf { it.format == format && it.state != DownloadEntity.DONE }
        dao.upsert(
            DownloadEntity(
                fileId = fileId,
                courseSlug = courseSlug,
                format = format,
                state = DownloadEntity.QUEUED,
                progressBytes = prior?.progressBytes ?: 0L,
                totalBytes = prior?.totalBytes ?: DownloadEntity.UNKNOWN_TOTAL,
                localPath = null,
                etag = prior?.etag,
                error = null,
                updatedAt = clock(),
            ),
        )
        if (existing != null && prior == null) deleteFiles(existing) // format switch: old bytes are useless
        scheduler.enqueue(courseSlug, fileId, format, wifiOnly)
    }

    fun observe(fileId: String): Flow<DownloadState> = dao.observe(fileId).map { it.toState() }.distinctUntilChanged()

    fun observeAll(): Flow<List<DownloadItem>> = dao.observeAll().map { rows -> rows.map { it.toItem() } }

    /** Cancels the transfer and deletes the row plus both the partial and the finished file. */
    suspend fun remove(fileId: String) {
        scheduler.cancel(fileId)
        val row = dao.get(fileId)
        dao.delete(fileId)
        row?.let { deleteFiles(it) }
    }

    suspend fun removeAll() {
        scheduler.cancelAll()
        val rows = dao.getAll()
        dao.deleteAll()
        rows.forEach { deleteFiles(it) }
    }

    /**
     * Cancels all download work and drops every row that is not [DownloadEntity.DONE] with its partial file; finished
     * downloads stay. Sign-out uses this so no stale running row outlives the session.
     */
    suspend fun removeUnfinished() {
        scheduler.cancelAll()
        dao.getAll().filter { it.state != DownloadEntity.DONE }.forEach {
            dao.delete(it.fileId)
            deleteFiles(it)
        }
    }

    /** [removeAll] plus the whole downloads directory, so files that have no row (orphans) go too. */
    suspend fun purgeAll() {
        removeAll()
        withContext(Dispatchers.IO) { paths.root.deleteRecursively() }
    }

    /** Total bytes held by downloads (finished files plus partials). */
    suspend fun storageBytes(): Long = dao.storageBytes()

    fun observeStorageBytes(): Flow<Long> = dao.observeStorageBytes().distinctUntilChanged()

    /** The finished file for offline open (#38), or null when it is not (or no longer) on disk. */
    suspend fun localFile(fileId: String): File? =
        dao
            .get(fileId)
            ?.takeIf { it.state == DownloadEntity.DONE }
            ?.localPath
            ?.let(::File)
            ?.takeIf { it.exists() }

    private suspend fun deleteFiles(row: DownloadEntity) =
        withContext(Dispatchers.IO) {
            paths.finalFile(row.courseSlug, row.fileId, row.format).delete()
            paths.partFile(row.courseSlug, row.fileId, row.format).delete()
        }
}
