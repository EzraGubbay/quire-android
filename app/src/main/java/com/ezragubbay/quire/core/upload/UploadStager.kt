package com.ezragubbay.quire.core.upload

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.UUID

data class UriMeta(
    val displayName: String?,
    val size: Long?,
    val mime: String?,
)

/** Seam over [ContentResolver] so staging is testable without a content provider. */
interface UriSource {
    fun query(uri: Uri): UriMeta

    fun open(uri: Uri): InputStream
}

class ContentResolverUriSource(
    private val resolver: ContentResolver,
) : UriSource {
    override fun query(uri: Uri): UriMeta {
        var name: String? = null
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                c
                    .getColumnIndex(OpenableColumns.SIZE)
                    .takeIf { it >= 0 && !c.isNull(it) }
                    ?.let { size = c.getLong(it) }
            }
        }
        return UriMeta(name, size, resolver.getType(uri))
    }

    override fun open(uri: Uri): InputStream = resolver.openInputStream(uri) ?: throw IOException("Could not open $uri")
}

sealed interface StageResult {
    data class Staged(
        val id: String,
        val displayName: String,
        val format: UploadFormat,
        val target: UploadTarget,
        val savedToDocuments: Boolean,
        val file: File,
        val sizeBytes: Long,
    ) : StageResult

    data class Rejected(
        val displayName: String,
        val reason: UploadRejection,
    ) : StageResult
}

/**
 * Copies a picked file into app-private storage before it is queued: the picker's URI grant may expire and a
 * provider such as Drive may be slow or offline later. The size limit is enforced while copying.
 */
class UploadStager(
    private val source: UriSource,
    private val paths: UploadPaths,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun stage(
        uri: Uri,
        tab: UploadTab,
    ): StageResult {
        val meta =
            try {
                source.query(uri)
            } catch (_: Exception) {
                return StageResult.Rejected(uri.lastPathSegment.orEmpty(), UploadRejection.UNREADABLE)
            }
        val name = meta.displayName?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "upload"
        return try {
            source.open(uri).use { raw -> stageStream(BufferedInputStream(raw), meta, name, tab) }
        } catch (_: IOException) {
            StageResult.Rejected(name, UploadRejection.UNREADABLE)
        } catch (_: SecurityException) {
            StageResult.Rejected(name, UploadRejection.UNREADABLE)
        }
    }

    @Suppress("ReturnCount") // guard clauses: each early return is a rejection
    private fun stageStream(
        input: BufferedInputStream,
        meta: UriMeta,
        name: String,
        tab: UploadTab,
    ): StageResult {
        input.mark(HEAD_BYTES)
        val head = ByteArray(HEAD_BYTES)
        val read = input.readUpTo(head)
        input.reset()
        val classified = UploadKind.classify(name, meta.mime, head.copyOf(read), tab)
        if (classified is Classified.Rejected) return StageResult.Rejected(name, classified.reason)
        classified as Classified.Accepted
        val limit = UploadLimits.maxFor(classified.format)
        if (meta.size != null && meta.size > limit) return StageResult.Rejected(name, tooLarge(classified.format))
        val id = newId()
        val file = paths.staged(id)
        file.parentFile?.mkdirs()
        val size =
            try {
                copyCapped(input, file, limit)
            } catch (e: IOException) {
                file.delete()
                throw e
            }
        val rejection =
            when {
                size < 0 -> tooLarge(classified.format)
                size == 0L -> UploadRejection.EMPTY
                classified.format == UploadFormat.MARKDOWN && !file.isUtf8() -> UploadRejection.NOT_UTF8
                else -> null
            }
        if (rejection != null) {
            file.delete()
            return StageResult.Rejected(name, rejection)
        }
        return StageResult.Staged(
            id,
            name,
            classified.format,
            classified.target,
            classified.savedToDocuments,
            file,
            size,
        )
    }

    /** Bytes copied, or -1 when [limit] was exceeded (the partial file is deleted). */
    private fun copyCapped(
        input: InputStream,
        target: File,
        limit: Long,
    ): Long {
        var total = 0L
        val buf = ByteArray(BUFFER)
        target.outputStream().use { out ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > limit) {
                    target.delete()
                    return -1L
                }
                out.write(buf, 0, n)
            }
        }
        return total
    }

    private fun tooLarge(format: UploadFormat) =
        if (format == UploadFormat.PDF) UploadRejection.PDF_TOO_LARGE else UploadRejection.MARKDOWN_TOO_LARGE

    private fun InputStream.readUpTo(buf: ByteArray): Int {
        var n = 0
        while (n < buf.size) {
            val r = read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    private fun File.isUtf8(): Boolean =
        try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(readBytes()))
            true
        } catch (_: CharacterCodingException) {
            false
        }

    private companion object {
        const val HEAD_BYTES = 16
        const val BUFFER = 32 * 1024
    }
}
