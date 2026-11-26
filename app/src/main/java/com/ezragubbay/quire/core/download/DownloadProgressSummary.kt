package com.ezragubbay.quire.core.download

import com.ezragubbay.quire.core.database.DownloadEntity

/** Aggregate of the downloads that are actively transferring, for the ongoing progress notification. */
data class DownloadProgressSummary(
    val running: Int,
    val queued: Int,
    val progressBytes: Long,
    /** Sum of the running totals, or null when any running download has an unknown size. */
    val totalBytes: Long?,
) {
    /** Whole percent 0..100, or null (indeterminate) when the total is unknown. */
    val percent: Int?
        get() = totalBytes?.takeIf { it > 0 }?.let { ((progressBytes.coerceAtMost(it) * 100) / it).toInt() }
}

/** A running row not updated for this long is treated as orphaned (worker stopped, process died). */
const val DOWNLOAD_STALE_MS = 60_000L

/**
 * Null when nothing is actively transferring. Only fresh `RUNNING` rows count: the worker bumps `updatedAt` every
 * 256 KB, so a stale one has no worker behind it and must not pin an ongoing notification. `QUEUED` rows are only
 * counted (they never show the notification on their own).
 */
fun summarize(
    rows: List<DownloadEntity>,
    now: Long,
    staleMs: Long = DOWNLOAD_STALE_MS,
): DownloadProgressSummary? {
    val running = rows.filter { it.state == DownloadEntity.RUNNING && now - it.updatedAt <= staleMs }
    if (running.isEmpty()) return null
    val known = running.all { it.totalBytes > 0 }
    return DownloadProgressSummary(
        running = running.size,
        queued = rows.count { it.state == DownloadEntity.QUEUED },
        progressBytes = running.sumOf { it.progressBytes },
        totalBytes = if (known) running.sumOf { it.totalBytes } else null,
    )
}
