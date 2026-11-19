package com.ezragubbay.quire.feature.viewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ezragubbay.quire.feature.viewer.pdf.OpenWithRequest

/** Hands an original to another app: `ACTION_VIEW` on a [FileProvider] URI, shown in a chooser. */
object OpenWithLauncher {
    /** True when the chooser was launched; false when no installed app handles the type. */
    fun launch(
        context: Context,
        request: OpenWithRequest,
    ): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", request.file)
        val view =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, request.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context.packageManager.queryIntentActivities(view, 0).isEmpty()) return false
        return try {
            context.startActivity(
                Intent.createChooser(view, request.title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
