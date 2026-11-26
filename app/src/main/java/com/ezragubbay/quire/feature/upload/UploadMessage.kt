package com.ezragubbay.quire.feature.upload

import androidx.annotation.StringRes
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.upload.EnqueueResult
import com.ezragubbay.quire.core.upload.UploadRejection

/** A one-shot note about a pick: a refused file, or a PDF that went to Documents instead of Notes. */
sealed interface UploadMessage {
    data class Rejected(
        val displayName: String,
        val reason: UploadRejection,
    ) : UploadMessage

    data class SavedToDocuments(
        val displayName: String,
    ) : UploadMessage
}

/** The messages an [EnqueueResult] calls for, refusals first. */
fun EnqueueResult.messages(): List<UploadMessage> =
    rejections.map { UploadMessage.Rejected(it.displayName, it.reason) } +
        savedToDocuments.map { UploadMessage.SavedToDocuments(it) }

@StringRes
internal fun rejectionRes(reason: UploadRejection): Int =
    when (reason) {
        UploadRejection.UNSUPPORTED -> R.string.upload_reject_unsupported
        UploadRejection.NOT_A_PDF -> R.string.upload_reject_not_pdf
        UploadRejection.PDF_TOO_LARGE -> R.string.upload_reject_pdf_large
        UploadRejection.MARKDOWN_TOO_LARGE -> R.string.upload_reject_markdown_large
        UploadRejection.NOT_UTF8 -> R.string.upload_reject_not_utf8
        UploadRejection.EMPTY -> R.string.upload_reject_empty
        UploadRejection.UNREADABLE -> R.string.upload_reject_unreadable
    }
