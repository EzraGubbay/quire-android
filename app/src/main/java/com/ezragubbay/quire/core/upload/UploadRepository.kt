package com.ezragubbay.quire.core.upload

import android.net.Uri
import com.ezragubbay.quire.core.database.UploadDao
import com.ezragubbay.quire.core.database.UploadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

class UploadRepository(
    private val dao: UploadDao,
    private val scheduler: UploadScheduler,
    private val paths: UploadPaths,
    private val stager: UploadStager,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : UploadQueue {
    override fun observe(courseSlug: String): Flow<List<UploadItem>> =
        dao.observeForCourse(courseSlug).map { rows -> rows.filter { it.state != UploadEntity.DONE }.map(::toItem) }

    override suspend fun enqueue(
        courseSlug: String,
        tab: UploadTab,
        uris: List<Uri>,
    ): EnqueueResult {
        val ids = mutableListOf<String>()
        val rejections = mutableListOf<UploadRejected>()
        val toDocuments = mutableListOf<String>()
        for (uri in uris) {
            when (val staged = stager.stage(uri, tab)) {
                is StageResult.Rejected -> rejections += UploadRejected(staged.displayName, staged.reason)
                is StageResult.Staged -> {
                    val now = clock()
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
                            resultId = null,
                            error = null,
                            serverFailed = false,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    scheduler.enqueue(staged.id)
                    ids += staged.id
                    if (staged.savedToDocuments) toDocuments += staged.displayName
                }
            }
        }
        return EnqueueResult(ids, rejections, toDocuments)
    }

    override suspend fun retry(id: String) {
        val row = dao.get(id)?.takeIf { it.state == UploadEntity.FAILED } ?: return
        val now = clock()
        if (row.serverFailed) {
            // A server-side `failed` job is final for its idempotency key: send the same staged file under a new one.
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
            dao.update(row.copy(state = UploadEntity.QUEUED, progressBytes = 0, error = null, updatedAt = now))
            scheduler.enqueue(row.id)
        }
    }

    override suspend fun cancel(id: String) {
        scheduler.cancel(id)
        remove(id)
    }

    override suspend fun dismiss(id: String) = cancel(id)

    override suspend fun clearAll() {
        dao.deleteAll()
        paths.deleteAll()
        scheduler.cancelAll()
    }

    private suspend fun remove(id: String) {
        val row = dao.get(id)
        dao.delete(id)
        row?.let { File(it.localPath).delete() }
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
