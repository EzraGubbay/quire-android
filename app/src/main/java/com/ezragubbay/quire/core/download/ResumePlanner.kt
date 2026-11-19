package com.ezragubbay.quire.core.download

import com.ezragubbay.quire.core.database.DownloadEntity

/** The conditional-range headers for a (possibly resumed) content request. Null means "omit the header". */
data class RangeRequest(
    val range: String?,
    val ifRange: String?,
)

/** What to do with a content response, given the bytes already on disk. */
sealed interface ResumeOutcome {
    /** 206 continuing exactly at the offset: append; [total] is the full size or [DownloadEntity.UNKNOWN_TOTAL]. */
    data class Append(
        val total: Long,
    ) : ResumeOutcome

    /** 200: the server sent the whole body (first download, or the ETag changed): truncate and write from zero. */
    data class Restart(
        val total: Long,
    ) : ResumeOutcome

    /** 416 or a mismatched 206: drop the partial file and ask again from zero. */
    data object Unsatisfiable : ResumeOutcome

    /** Any other status; the caller classifies it. */
    data class Unexpected(
        val code: Int,
    ) : ResumeOutcome
}

/** Pure Range / If-Range request building and response interpretation. */
object ResumePlanner {
    private const val OK = 200
    private const val PARTIAL = 206
    private const val UNSATISFIABLE = 416
    private val contentRange = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")

    /**
     * Resume only with a validator: bytes without an ETag cannot be trusted to belong to the current file, so
     * they restart. `If-Range` makes the server answer 200 (whole body) instead of 206 when the ETag changed.
     */
    fun plan(
        partBytes: Long,
        etag: String?,
    ): RangeRequest =
        if (partBytes > 0 && !etag.isNullOrBlank()) {
            RangeRequest("bytes=$partBytes-", etag)
        } else {
            RangeRequest(null, null)
        }

    fun interpret(
        code: Int,
        contentRangeHeader: String?,
        contentLength: Long,
        partBytes: Long,
    ): ResumeOutcome =
        when (code) {
            OK -> ResumeOutcome.Restart(contentLength.takeIf { it >= 0 } ?: DownloadEntity.UNKNOWN_TOTAL)
            PARTIAL -> {
                val m = contentRangeHeader?.let { contentRange.matchEntire(it.trim()) }
                val (start, _, total) = m?.destructured ?: return ResumeOutcome.Unsatisfiable
                if (start.toLongOrNull() == partBytes && partBytes > 0) {
                    ResumeOutcome.Append(total.toLongOrNull() ?: DownloadEntity.UNKNOWN_TOTAL)
                } else {
                    ResumeOutcome.Unsatisfiable
                }
            }
            UNSATISFIABLE -> ResumeOutcome.Unsatisfiable
            else -> ResumeOutcome.Unexpected(code)
        }
}
