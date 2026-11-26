package com.ezragubbay.quire.core.upload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ezragubbay.quire.core.database.UploadDao
import com.ezragubbay.quire.core.database.UploadEntity
import com.ezragubbay.quire.core.network.ErrorEnvelope
import com.ezragubbay.quire.core.network.InsecureServerException
import com.ezragubbay.quire.core.network.NoServerConfiguredException
import com.ezragubbay.quire.core.network.QuireJson
import com.ezragubbay.quire.core.network.UploadApi
import com.ezragubbay.quire.core.network.UploadJobDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.IOException

/** Everything an [UploadWorker] needs; supplied by [UploadWorkerFactory] (or directly in tests). */
class UploadDeps(
    val dao: UploadDao,
    val api: UploadApi,
    val paths: UploadPaths,
    /** Force-refreshes the course so the new file or note shows up; best effort. */
    val refresh: suspend (courseSlug: String) -> Unit = {},
    val clock: () -> Long = System::currentTimeMillis,
    /** Waits between polls of the server job; one run polls once per entry, then yields to a retry. */
    val pollDelaysMillis: List<Long> = DEFAULT_POLL_DELAYS_MILLIS,
) {
    companion object {
        val DEFAULT_POLL_DELAYS_MILLIS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L)
    }
}

/**
 * Uploads one queued file: POST the staged copy (idempotent by the row id), then poll the server job until it is
 * `done` or `failed`. The Room row is the source of truth. Transient failures (network, 5xx, a job still processing
 * after the poll window) return [Result.retry]; the next run skips the POST once the server accepted the job.
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters,
    private val deps: UploadDeps,
) : CoroutineWorker(context, params) {
    private val uploadId = inputData.getString(KEY_UPLOAD_ID).orEmpty()

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val row = deps.dao.get(uploadId) ?: return@withContext Result.failure()
            try {
                run(row)
            } catch (e: CancellationException) {
                throw e // the row keeps its state: the next run resumes (a repeated POST is idempotent)
            } catch (e: NoServerConfiguredException) {
                fail(e.message ?: "No server configured")
            } catch (e: InsecureServerException) {
                fail(e.message ?: "Insecure server")
            } catch (e: IOException) {
                transient(e.message ?: "Network error")
            }
        }

    @Suppress("ReturnCount") // guard clauses: each early return is a terminal outcome
    private suspend fun run(row: UploadEntity): Result {
        val serverId =
            row.serverUploadId ?: run {
                val file = File(row.localPath)
                if (!file.exists()) return fail("The file is no longer available; pick it again")
                val response = post(row, file)
                if (!response.isSuccessful) return classify(response.code(), response.errorBody(), isPost = true)
                val job = response.body() ?: return transient("Empty response")
                mutate { it.copy(serverUploadId = job.uploadId, state = UploadEntity.PROCESSING, error = null) }
                settle(job)?.let { return it }
                job.uploadId
            }
        return poll(serverId)
    }

    private suspend fun post(
        row: UploadEntity,
        file: File,
    ): Response<UploadJobDto> =
        coroutineScope {
            mutate { it.copy(state = UploadEntity.UPLOADING, progressBytes = 0, error = null) }
            val sent = MutableStateFlow(0L)
            val reporter = launch { sent.collect { p -> mutate { it.copy(progressBytes = p) } } }
            try {
                deps.api.create(
                    row.courseSlug,
                    row.id,
                    row.target,
                    row.displayName,
                    ProgressRequestBody(
                        file,
                        UploadFormat.entries
                            .first { it.wire == row.format }
                            .contentType
                            .toMediaType(),
                    ) {
                        sent.value = it
                    },
                )
            } finally {
                reporter.cancel()
            }
        }

    @Suppress("ReturnCount") // guard clauses: each early return is a terminal outcome
    private suspend fun poll(serverId: String): Result {
        for (wait in deps.pollDelaysMillis) {
            val response = deps.api.job(serverId)
            if (!response.isSuccessful) return classify(response.code(), response.errorBody(), isPost = false)
            val job = response.body() ?: return transient("Empty response")
            settle(job)?.let { return it }
            delay(wait)
        }
        mutate { it.copy(state = UploadEntity.PROCESSING) }
        return Result.retry()
    }

    /** A final result for [job] (done / failed), or null while it is still queued or processing. */
    private suspend fun settle(job: UploadJobDto): Result? =
        when (job.status) {
            STATUS_DONE -> finish()
            STATUS_FAILED -> {
                mutate {
                    it.copy(
                        state = UploadEntity.FAILED,
                        error = job.error?.takeIf(String::isNotBlank) ?: "The server could not process the file",
                        serverFailed = true,
                    )
                }
                Result.failure()
            }
            else -> null
        }

    private suspend fun finish(): Result {
        val row = deps.dao.get(uploadId)
        val staged = row?.localPath?.let(::File)
        row?.courseSlug?.let {
            try {
                deps.refresh(it)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort: the list refreshes on the next sync anyway.
            }
        }
        staged?.delete()
        deps.dao.delete(uploadId)
        return if (row == null) Result.failure() else Result.success()
    }

    /** Read-modify-write of this upload's row; a no-op when it was cancelled meanwhile. */
    private suspend fun mutate(change: (UploadEntity) -> UploadEntity) {
        val row = deps.dao.get(uploadId) ?: return
        deps.dao.update(change(row).copy(updatedAt = deps.clock()))
    }

    private suspend fun classify(
        code: Int,
        errorBody: ResponseBody?,
        isPost: Boolean,
    ): Result {
        val message = errorBody?.let { serverMessage(it) }
        return when {
            code == HTTP_UNAUTHORIZED -> fail("Signed out: pair this device again")
            code == HTTP_NOT_FOUND ->
                fail(
                    if (isPost) "Course not found on the server" else "Upload not found on the server",
                )
            code == HTTP_BAD_REQUEST || code == HTTP_TOO_LARGE ->
                fail(
                    message ?: "The server rejected the file ($code)",
                )
            code >= HTTP_SERVER_ERROR || code == HTTP_TOO_MANY -> transient("Server error ($code)")
            else -> fail("Unexpected response ($code)")
        }
    }

    /** Keeps the row queued for retry until attempts run out, then records FAILED (retryable with the same key). */
    private suspend fun transient(message: String): Result {
        if (runAttemptCount + 1 >= MAX_ATTEMPTS) return fail(message)
        mutate { it.copy(state = UploadEntity.QUEUED, error = message) }
        return Result.retry()
    }

    private suspend fun fail(message: String): Result {
        mutate { it.copy(state = UploadEntity.FAILED, error = message) }
        return Result.failure()
    }

    companion object {
        const val KEY_UPLOAD_ID = "uploadId"
        const val MAX_ATTEMPTS = 5
        private const val STATUS_DONE = "done"
        private const val STATUS_FAILED = "failed"
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_LARGE = 413
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_SERVER_ERROR = 500
    }
}

private fun serverMessage(body: ResponseBody): String? =
    try {
        body.use {
            QuireJson
                .decodeFromString<ErrorEnvelope>(it.string())
                .error.message
                .ifBlank { null }
        }
    } catch (_: Exception) {
        null
    }
