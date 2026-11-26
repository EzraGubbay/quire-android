package com.ezragubbay.quire.feature.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ezragubbay.quire.core.upload.UploadTab

/**
 * MIME types offered by the system picker. Providers often report Markdown as plain text or an opaque binary, so
 * those are offered too; the stager then classifies by extension and content. Both tabs offer PDFs: one picked on
 * Notes is uploaded as a document.
 */
internal val PICKER_MIME_TYPES =
    arrayOf("application/pdf", "text/markdown", "text/x-markdown", "text/plain", "application/octet-stream")

/** Returns a function that opens the system document picker (multi-select) and reports the picked URIs. */
@Composable
fun rememberUploadPicker(
    tab: UploadTab,
    onPicked: (List<Uri>) -> Unit,
): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) onPicked(uris)
        }
    return remember(launcher, tab) { { launcher.launch(PICKER_MIME_TYPES) } }
}
