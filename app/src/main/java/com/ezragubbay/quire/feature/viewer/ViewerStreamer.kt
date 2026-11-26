package com.ezragubbay.quire.feature.viewer

import com.ezragubbay.quire.core.network.ContentApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Streams the content endpoint into a temporary file (write to `.part`, move into place when complete). Cancellable,
 * and deliberately free of Room/WorkManager: this is viewing, not downloading.
 *
 * A cached copy is revalidated on every open with `If-None-Match` against the `ETag` stored in a sidecar next to it:
 * `304` keeps the copy, `200` replaces it. Revalidation is stale-if-error: offline, a non-200/304 status, a response
 * slower than [revalidateTimeoutMillis] or a replacement that breaks halfway all serve the existing copy. Only a
 * cache miss can fail an open.
 */
class ViewerStreamer(
    private val api: ContentApi,
    private val cache: ViewerCache,
    private val revalidateTimeoutMillis: Long = DEFAULT_REVALIDATE_TIMEOUT_MILLIS,
) {
    /**
     * The readable file for [source]: a download as-is, or a streamed temporary copy (revalidated when already
     * cached). Cache hits are LRU-touched; the cache is trimmed afterwards.
     */
    suspend fun resolve(
        source: OpenSource,
        courseSlug: String,
        fileId: String,
        title: String?,
        onProgress: (Long, Long?) -> Unit,
    ): File =
        when (source) {
            is OpenSource.Local -> source.file
            is OpenSource.Stream -> {
                val target = cache.fileFor(fileId, source.format, title)
                if (target.exists() && target.length() > 0) {
                    revalidate(courseSlug, fileId, source.format, target, onProgress)
                    target.setLastModified(System.currentTimeMillis())
                } else {
                    fetch(courseSlug, fileId, source.format, target, onProgress)
                }
                cache.trim(keep = target)
                target
            }
        }

    /** Drops [file] and its validator, e.g. when a cached copy turns out to be unreadable. */
    fun discard(file: File) {
        file.delete()
        cache.etagFile(file).delete()
    }

    /** [onProgress] gets (bytesSoFar, totalOrNull). Throws [IOException] on failure; deletes the partial file. */
    suspend fun fetch(
        courseSlug: String,
        fileId: String,
        format: String,
        target: File,
        onProgress: (Long, Long?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val response = api.content(courseSlug, fileId, format, null, null)
        store(response, target, onProgress)
    }

    /**
     * Asks the server whether the cached [target] is still current and replaces it on a `200`. Never throws for a
     * network or HTTP problem (the cached copy stands); a caller cancellation still propagates.
     */
    private suspend fun revalidate(
        courseSlug: String,
        fileId: String,
        format: String,
        target: File,
        onProgress: (Long, Long?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val etag = cache.readEtag(target)
        val response =
            try {
                // Bounds only the wait for the response headers (the call is streaming); the body copy is not timed.
                withTimeoutOrNull(revalidateTimeoutMillis) {
                    api.contentIfNoneMatch(courseSlug, fileId, format, etag)
                }
            } catch (_: IOException) {
                null
            } ?: return@withContext
        if (response.code() != HTTP_OK || response.body() == null) {
            response.body()?.close()
            response.errorBody()?.close()
            return@withContext // 304 (unchanged), or an error status: keep the cached copy
        }
        try {
            store(response, target, onProgress)
        } catch (_: IOException) {
            // The replacement broke halfway: store() removed the partial file and left the old copy and ETag alone.
        }
    }

    /**
     * Streams a successful [response] to `<target>.part`, then swaps it in and records its `ETag`. The old validator
     * is dropped before the move so a crash in between leaves a copy with no validator (the next open refetches
     * unconditionally) rather than new bytes paired with an old ETag.
     */
    private suspend fun store(
        response: Response<ResponseBody>,
        target: File,
        onProgress: (Long, Long?) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        var finished = false
        try {
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                response.errorBody()?.close()
                throw IOException("Content request failed (${response.code()})")
            }
            copyBody(body, part, onProgress)
            cache.writeEtag(target, null)
            Files.move(
                part.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            cache.writeEtag(target, response.headers()["ETag"])
            finished = true
        } finally {
            if (!finished) part.delete()
        }
    }

    private suspend fun copyBody(
        body: ResponseBody,
        part: File,
        onProgress: (Long, Long?) -> Unit,
    ) {
        val total = body.contentLength().takeIf { it > 0 }
        body.use { b ->
            b.byteStream().use { input -> part.outputStream().use { out -> pump(input, out, total, onProgress) } }
        }
    }

    private suspend fun pump(
        input: InputStream,
        out: OutputStream,
        total: Long?,
        onProgress: (Long, Long?) -> Unit,
    ) {
        val buf = ByteArray(BUFFER)
        var read = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            read += n
            onProgress(read, total)
        }
        if (total != null && read != total) throw IOException("Truncated content")
    }

    private companion object {
        const val BUFFER = 16 * 1024
        const val HTTP_OK = 200
        const val DEFAULT_REVALIDATE_TIMEOUT_MILLIS = 4_000L
    }
}
