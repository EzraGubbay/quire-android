package com.ezragubbay.quire.core.upload

import android.net.Uri
import com.ezragubbay.quire.core.database.UploadDao
import com.ezragubbay.quire.core.database.UploadEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** One row of the course tab's "Uploads" section. */
data class UploadItem(
    val id: String,
    val displayName: String,
    val target: UploadTarget,
    val state: State,
    val progressBytes: Long,
    val sizeBytes: Long,
    val error: String?,
) {
    enum class State { QUEUED, UPLOADING, PROCESSING, FAILED }
}

/** A picked file that was not enqueued, with why. */
data class UploadRejected(
    val displayName: String,
    val reason: UploadRejection,
)

data class EnqueueResult(
    val enqueuedIds: List<String>,
    val rejections: List<UploadRejected>,
    /** PDFs picked on the Notes tab: uploaded as documents, and the user is told so. */
    val savedToDocuments: List<String>,
)

/** What the UI and the session lifecycle need from the upload queue (a seam, so view models test without Room). */
interface UploadQueue {
    fun observe(courseSlug: String): Flow<List<UploadItem>>

    suspend fun enqueue(
        courseSlug: String,
        tab: UploadTab,
        uris: List<Uri>,
    ): EnqueueResult

    suspend fun retry(id: String)

    /** Cancels a pending upload and removes it. A job the server already accepted still completes there. */
    suspend fun cancel(id: String)

    /** Removes a failed upload. */
    suspend fun dismiss(id: String)

    /** Cancels and removes everything, including staged copies (sign-out, session expiry). */
    suspend fun clearAll()
}

@Suppress("LongParameterList") // collaborators plus test seams (clock, ids, dispatcher), all defaulted
class UploadRepository(
    private val dao: UploadDao,
    private val scheduler: UploadScheduler,
    private val paths: UploadPaths,
    private val stager: UploadStager,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Staging reads and copies up to 100 MB: it must never run on the caller's (main) thread. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : UploadQueue {
    /** Serialises staging, row swaps and the orphan sweep, so the sweep never deletes a just-staged file. */
    private val lock = Mutex()

    override fun observe(courseSlug: String): Flow<List<UploadItem>> =
        dao.observeForCourse(courseSlug).map { rows -> rows.filter { it.state != UploadEntity.DONE }.map(::toItem) }

    override suspend fun enqueue(
        courseSlug: String,
        tab: UploadTab,
        uris: List<Uri>,
    ): EnqueueResult =
        // Not cancellable: leaving the screen mid-pick must not strand a staged file without a row.
        withContext(io + NonCancellable) {
            lock.withLock {
                sweepOrphans()
                val ids = mutableListOf<String>()
                val rejections = mutableListOf<UploadRejected>()
                val toDocuments = mutableListOf<String>()
                for (uri in uris) {
                    when (val staged = stager.stage(uri, tab)) {
                        is StageResult.Rejected -> rejections += UploadRejected(staged.displayName, staged.reason)
                        is StageResult.Staged -> {
                            insertQueued(courseSlug, staged)
                            ids += staged.id
                            if (staged.savedToDocuments) toDocuments += staged.displayName
                        }
                    }
                }
                EnqueueResult(ids, rejections, toDocuments)
            }
        }

    /** Inserts the row and schedules its work; a failed insert deletes the staged copy so nothing is orphaned. */
    @Suppress("TooGenericExceptionCaught") // cleanup then rethrow: any failure must not leave an orphan
    private suspend fun insertQueued(
        courseSlug: String,
        staged: StageResult.Staged,
    ) {
        val now = clock()
        try {
            dao.insert(
                UploadEntity(
                    id = staged.id,
                    courseSlug = courseSlug,
                    target = staged.target.wire,
                    format = staged.format.wire,
                    displayName = staged.displayName,
                    localPath = staged.file.absolutePath,
                    sizeBytes = staged.sizeBytes,
                    state = UploadEntity.QUEUED,
                    progressBytes = 0,
                    serverUploadId = null,
                    error = null,
                    serverFailed = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } catch (e: Exception) {
            staged.file.delete()
            throw e
        }
        scheduler.enqueue(staged.id)
    }

    /** Deletes staged files that no row refers to (a process death between copy and insert leaves one behind). */
    private suspend fun sweepOrphans() {
        val known = dao.getAll().map { File(it.localPath).absolutePath }.toSet()
        paths.root.listFiles()?.forEach { if (it.absolutePath !in known) it.delete() }
    }

    override suspend fun retry(id: String) {
        lock.withLock {
            val row = dao.get(id)?.takeIf { it.state == UploadEntity.FAILED } ?: return
            val now = clock()
            if (row.serverFailed) {
                // A server-side `failed` job is final for its idempotency key: resend the staged file under a new one.
                val fresh =
                    row.copy(
                        id = newId(),
                        state = UploadEntity.QUEUED,
                        progressBytes = 0,
                        serverUploadId = null,
                        error = null,
                        serverFailed = false,
                        createdAt = now,
                        updatedAt = now,
                    )
                dao.delete(row.id)
                dao.insert(fresh)
                scheduler.enqueue(fresh.id)
            } else {
                // The same key goes out again: a job the server already accepted is simply returned by the POST.
                dao.update(
                    row.copy(
                        state = UploadEntity.QUEUED,
                        progressBytes = 0,
                        error = null,
                        serverUploadId = null,
                        updatedAt = now,
                    ),
                )
                scheduler.enqueue(row.id)
            }
        }
    }

    override suspend fun cancel(id: String) {
        scheduler.cancel(id)
        lock.withLock {
            val row = dao.get(id)
            dao.delete(id)
            row?.let { withContext(io) { File(it.localPath).delete() } }
        }
    }

    override suspend fun dismiss(id: String) = cancel(id)

    override suspend fun clearAll() {
        try {
            scheduler.cancelAll()
        } finally {
            lock.withLock {
                dao.deleteAll()
                withContext(io) { paths.deleteAll() }
            }
        }
    }

    private fun toItem(row: UploadEntity) =
        UploadItem(
            id = row.id,
            displayName = row.displayName,
            target = UploadTarget.entries.first { it.wire == row.target },
            state =
                when (row.state) {
                    UploadEntity.UPLOADING -> UploadItem.State.UPLOADING
                    UploadEntity.PROCESSING -> UploadItem.State.PROCESSING
                    UploadEntity.FAILED -> UploadItem.State.FAILED
                    else -> UploadItem.State.QUEUED
                },
            progressBytes = row.progressBytes,
            sizeBytes = row.sizeBytes,
            error = row.error,
        )
}
