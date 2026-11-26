package com.ezragubbay.quire.core.upload

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import java.io.File

/** Streams a staged file as the request body and reports bytes sent every [REPORT_EVERY] bytes. */
class ProgressRequestBody(
    private val file: File,
    private val mediaType: MediaType,
    private val onProgress: (sent: Long) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType = mediaType

    override fun contentLength(): Long = file.length()

    /** Re-readable from the staged file, so OkHttp may transparently retry a dropped connection. */
    override fun isOneShot(): Boolean = false

    override fun writeTo(sink: BufferedSink) {
        file.source().use { source ->
            val buf = Buffer()
            var sent = 0L
            var sinceReport = 0L
            while (true) {
                val n = source.read(buf, SEGMENT)
                if (n < 0) break
                sink.write(buf, n)
                sent += n
                sinceReport += n
                if (sinceReport >= REPORT_EVERY) {
                    sinceReport = 0
                    onProgress(sent)
                }
            }
            onProgress(sent)
        }
    }

    private companion object {
        const val SEGMENT = 8L * 1024
        const val REPORT_EVERY = 256L * 1024
    }
}
