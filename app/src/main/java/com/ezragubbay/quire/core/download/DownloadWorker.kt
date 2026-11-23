package com.ezragubbay.quire.core.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ezragubbay.quire.core.database.DownloadDao
import com.ezragubbay.quire.core.database.DownloadEntity
import com.ezragubbay.quire.core.network.ContentApi
import com.ezragubbay.quire.core.network.InsecureServerException
import com.ezragubbay.quire.core.network.NoServerConfiguredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.coroutineContext

/** Everything a [DownloadWorker] needs; supplied by [DownloadWorkerFactory] (or directly in tests). */
class DownloadDeps(
    val dao: DownloadDao,
    val api: ContentApi,
    val paths: DownloadPaths,
    val clock: () -> Long = System::currentTimeMillis,
    val openSink: (file: File, append: Boolean) -> OutputStream = { f, append -> FileOutputStream(f, append) },
    /** Called once a download reaches a final state (done, or failed for good); pinned-course batches listen. */
    val onSettled: suspend (courseSlug: String, fileId: String, success: Boolean) -> Unit = { _, _, _ -> },
) {
    /** Runs [onSettled]; a listener failure must never fail the download itself. */
    suspend fun notifySettled(
        courseSlug: String,
        fileId: String,
        success: Boolean,
    ) {
        try {
            onSettled(courseSlug, fileId, success)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort: the summary notification is optional.
        }
    }
}

/**
 * Streams one file's content into `<name>.part`, resuming with `Range` + `If-Range` (stored ETag), then atomically
 * renames to the final file. The Room row is the source of truth. Transient failures (network, 5xx) return
 * [Result.retry] so WorkManager applies exponential backoff, up to [MAX_ATTEMPTS]; permanent ones record FAILED.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val deps: DownloadDeps,
) : CoroutineWorker(context, params) {
    private val fileId = inputData.getString(KEY_FILE_ID).orEmpty()
    private val courseSlug = inputData.getString(KEY_COURSE).orEmpty()
    private val format = inputData.getString(KEY_FORMAT).orEmpty()

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            if (deps.dao.get(fileId) == null) return@withContext Result.failure()
            try {
                run()
            } catch (e: CancellationException) {
                throw e // keep the .part and ETag: the next run resumes
            } catch (e: NoServerConfiguredException) {
                fail(e.message ?: "No server configured")
            } catch (e: InsecureServerException) {
                fail(e.message ?: "Insecure server")
            } catch (e: IOException) {
                if (isDiskFull(e)) fail("Not enough storage space") else transient(e.message ?: "Network error")
            }
        }

    private suspend fun run(): Result {
        val part = deps.paths.partFile(courseSlug, fileId, format)
        part.parentFile?.mkdirs()
        // A second pass happens only when the server rejected the resume (416 / bad 206): restart from zero.
        return attempt(part) ?: attempt(part) ?: fail("Server rejected the resume request")
    }

    /** One request/response cycle; null means "partial file discarded, ask again from zero". */
    private suspend fun attempt(part: File): Result? {
        val row = deps.dao.get(fileId) ?: return Result.failure()
        val have = if (part.exists()) part.length() else 0L
        val plan = ResumePlanner.plan(have, row.etag)
        if (have > 0 && plan.range == null) part.delete()
        val response = deps.api.content(courseSlug, fileId, format, plan.range, plan.ifRange)
        val body = response.body()
        val outcome =
            ResumePlanner.interpret(
                response.code(),
                response.headers()["Content-Range"],
                body?.contentLength() ?: UNKNOWN,
                if (plan.range == null) 0L else have,
            )
        return when (outcome) {
            is ResumeOutcome.Unsatisfiable -> {
                response.errorBody()?.close()
                body?.close()
                part.delete()
                mutate { it.copy(state = DownloadEntity.RUNNING, progressBytes = 0, totalBytes = UNKNOWN, etag = null) }
                null
            }
            is ResumeOutcome.Unexpected -> classify(outcome.code)
            is ResumeOutcome.Append -> stream(body, part, true, outcome.total, row.etag)
            is ResumeOutcome.Restart -> stream(body, part, false, outcome.total, response.headers()["ETag"])
        }
    }

    private suspend fun stream(
        body: ResponseBody?,
        part: File,
        append: Boolean,
        total: Long,
        etag: String?,
    ): Result {
        if (body == null) return transient("Empty response")
        val start = if (append) part.length() else 0L
        mutate { it.copy(state = DownloadEntity.RUNNING, progressBytes = start, totalBytes = total, etag = etag) }
        body.use { b -> deps.openSink(part, append).use { out -> copy(b, out, start, total) } }
        // Reflect the real on-disk length, so a failed length check below leaves resumable state.
        val onDisk = part.length()
        mutate { it.copy(progressBytes = onDisk) }
        return if (total >= 0 && onDisk != total) {
            transient("Incomplete download ($onDisk of $total bytes)")
        } else {
            finish(part, onDisk, etag)
        }
    }

    private suspend fun copy(
        body: ResponseBody,
        out: OutputStream,
        start: Long,
        total: Long,
    ) {
        val buf = ByteArray(BUFFER)
        val source = body.byteStream()
        var written = start
        var sinceReport = 0L
        var n = source.read(buf)
        while (n >= 0) {
            coroutineContext.ensureActive()
            out.write(buf, 0, n)
            written += n
            sinceReport += n
            if (sinceReport >= REPORT_EVERY) {
                sinceReport = 0
                mutate { it.copy(progressBytes = written) }
                setProgress(workDataOf(KEY_PROGRESS to written, KEY_TOTAL to total))
            }
            n = source.read(buf)
        }
    }

    private suspend fun finish(
        part: File,
        size: Long,
        etag: String?,
    ): Result {
        if (deps.dao.get(fileId) == null) {
            part.delete() // removed while streaming
            return Result.failure()
        }
        val target = deps.paths.finalFile(courseSlug, fileId, format)
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        mutate {
            it.copy(
                state = DownloadEntity.DONE,
                progressBytes = size,
                totalBytes = size,
                etag = etag,
                localPath = target.absolutePath,
                error = null,
            )
        }
        deps.notifySettled(courseSlug, fileId, true)
        return Result.success()
    }

    /** Read-modify-write of this file's row; a no-op when it was removed meanwhile. */
    private suspend fun mutate(change: (DownloadEntity) -> DownloadEntity) {
        val row = deps.dao.get(fileId) ?: return
        deps.dao.update(change(row).copy(updatedAt = deps.clock()))
    }

    private suspend fun classify(code: Int): Result =
        when {
            code == HTTP_NOT_FOUND -> fail("File not found on the server")
            code == HTTP_UNAUTHORIZED -> fail("Signed out: pair this device again")
            code >= HTTP_SERVER_ERROR || code == HTTP_TOO_MANY -> transient("Server error ($code)")
            else -> fail("Unexpected response ($code)")
        }

    /** Keeps the row RUNNING (queued for retry) until attempts run out, then records FAILED. */
    private suspend fun transient(message: String): Result {
        if (runAttemptCount + 1 >= MAX_ATTEMPTS) return fail(message)
        mutate { it.copy(state = DownloadEntity.QUEUED, error = message) }
        return Result.retry()
    }

    private suspend fun fail(message: String): Result {
        mutate { it.copy(state = DownloadEntity.FAILED, error = message) }
        deps.notifySettled(courseSlug, fileId, false)
        return Result.failure()
    }

    companion object {
        const val KEY_FILE_ID = "fileId"
        const val KEY_COURSE = "courseSlug"
        const val KEY_FORMAT = "format"
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL = "total"
        const val MAX_ATTEMPTS = 5
        private const val UNKNOWN = DownloadEntity.UNKNOWN_TOTAL
        private const val BUFFER = 32 * 1024
        private const val REPORT_EVERY = 256 * 1024L
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_SERVER_ERROR = 500
    }
}

private fun isDiskFull(e: IOException): Boolean =
    e.message.orEmpty().let { it.contains("No space left", true) || it.contains("ENOSPC", true) }
