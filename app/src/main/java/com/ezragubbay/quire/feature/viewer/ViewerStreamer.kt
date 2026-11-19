package com.ezragubbay.quire.feature.viewer

import com.ezragubbay.quire.core.network.ContentApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Streams the content endpoint into a temporary file (write to `.part`, rename when complete). Cancellable, and
 * deliberately free of Room/WorkManager: this is viewing, not downloading.
 */
class ViewerStreamer(
    private val api: ContentApi,
    private val cache: ViewerCache,
) {
    /**
     * The readable file for [source]: a download as-is, or a (possibly already cached) streamed temporary copy.
     * Cache hits are LRU-touched; the cache is trimmed after a fetch.
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
                    target.setLastModified(System.currentTimeMillis())
                } else {
                    fetch(courseSlug, fileId, source.format, target, onProgress)
                }
                cache.trim(keep = target)
                target
            }
        }

    /** [onProgress] gets (bytesSoFar, totalOrNull). Throws [IOException] on failure; deletes the partial file. */
    suspend fun fetch(
        courseSlug: String,
        fileId: String,
        format: String,
        target: File,
        onProgress: (Long, Long?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        var finished = false
        try {
            val response = api.content(courseSlug, fileId, format, null, null)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                response.errorBody()?.close()
                throw IOException("Content request failed (${response.code()})")
            }
            val total = body.contentLength().takeIf { it > 0 }
            body.use { b ->
                b.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(BUFFER)
                        var read = 0L
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            onProgress(read, total)
                        }
                        if (total != null && read != total) throw IOException("Truncated content")
                    }
                }
            }
            target.delete()
            if (!part.renameTo(target)) throw IOException("Could not finalize cache file")
            finished = true
        } finally {
            if (!finished) part.delete()
        }
    }

    private companion object {
        const val BUFFER = 16 * 1024
    }
}
