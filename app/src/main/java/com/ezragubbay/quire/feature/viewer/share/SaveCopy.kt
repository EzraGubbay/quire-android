package com.ezragubbay.quire.feature.viewer.share

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Writes a downloaded file's bytes to a Storage Access Framework destination the user picked. */
object SaveCopy {
    /** True when every byte was written. */
    suspend fun toUri(
        resolver: ContentResolver,
        source: File,
        destination: Uri,
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val out = checkNotNull(resolver.openOutputStream(destination, "w"))
                out.use { sink -> source.inputStream().use { StreamCopy.copy(it, sink) } }
            }.isSuccess
        }
}
