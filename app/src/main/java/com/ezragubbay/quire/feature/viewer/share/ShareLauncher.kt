package com.ezragubbay.quire.feature.viewer.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Shares a downloaded file through the same [FileProvider] authority as 'Open with...'. */
object ShareLauncher {
    /** True when the chooser was launched; false when nothing can receive the file. */
    fun share(
        context: Context,
        file: File,
        mime: String,
        title: String,
    ): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val chooser =
            Intent
                .createChooser(ShareIntents.send(uri, mime, title), title)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
