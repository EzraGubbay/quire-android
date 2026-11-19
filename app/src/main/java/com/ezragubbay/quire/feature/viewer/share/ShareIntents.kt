package com.ezragubbay.quire.feature.viewer.share

import android.content.Intent
import android.net.Uri

/** Builds the share and save-a-copy intents. Pure construction, no launching. */
object ShareIntents {
    /** `ACTION_SEND` of a [FileProvider][androidx.core.content.FileProvider] [uri], with a read grant. */
    fun send(
        uri: Uri,
        mime: String,
        title: String,
    ): Intent =
        Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Storage Access Framework 'save as': the user picks the destination; no storage permission needed. */
    fun createDocument(
        mime: String,
        fileName: String,
    ): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mime)
            .putExtra(Intent.EXTRA_TITLE, fileName)
}
