package com.ezragubbay.quire.core.download

import com.ezragubbay.quire.core.database.DownloadEntity

/** What the UI sees for one file. QUEUED rows surface as [Running] with zero progress. */
sealed interface DownloadState {
    /** Not downloaded (no row). */
    data object Idle : DownloadState

    /** Queued or streaming. [totalBytes] is null until the server reports a size. */
    data class Running(
        val progressBytes: Long,
        val totalBytes: Long?,
    ) : DownloadState {
        /** 0..1, or null while the total is unknown. */
        val fraction: Float? get() =
            totalBytes?.takeIf { it > 0 }?.let {
                (progressBytes.toFloat() / it).coerceIn(
                    0f,
                    1f,
                )
            }
    }

    /** Fully downloaded and length-checked; [localPath] is the final file. */
    data class Done(
        val localPath: String,
        val sizeBytes: Long,
    ) : DownloadState

    data class Failed(
        val reason: String,
    ) : DownloadState
}

/** One row of the Downloads tab. */
data class DownloadItem(
    val fileId: String,
    val courseSlug: String,
    val format: String,
    val state: DownloadState,
    val updatedAt: Long,
)

fun DownloadEntity?.toState(): DownloadState =
    when (this?.state) {
        null -> DownloadState.Idle
        DownloadEntity.DONE ->
            localPath?.let { DownloadState.Done(it, progressBytes) } ?: DownloadState.Failed("Missing file")
        DownloadEntity.FAILED -> DownloadState.Failed(error ?: "Download failed")
        else ->
            DownloadState.Running(
                progressBytes,
                totalBytes.takeIf { it != DownloadEntity.UNKNOWN_TOTAL },
            )
    }

fun DownloadEntity.toItem() = DownloadItem(fileId, courseSlug, format, toState(), updatedAt)
