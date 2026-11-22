package com.ezragubbay.quire.sync

/** The files auto-download may start now, and those held back because the storage cap would be exceeded. */
data class AutoDownloadPlan(
    val allowed: List<PinFile>,
    val blocked: List<PinFile>,
) {
    /** True when the cap stopped at least one file: auto-download is paused. */
    val paused: Boolean get() = blocked.isNotEmpty()
}

/** Pure storage-cap rules for pinned auto-download. */
object AutoDownloadPolicy {
    const val DEFAULT_CAP_BYTES = 2L * 1024 * 1024 * 1024

    /**
     * Takes [candidates] in order while [usedBytes] plus their sizes stay within [capBytes]. The first file that
     * would exceed it pauses auto-download: it and everything after it are blocked.
     */
    fun plan(
        candidates: List<PinFile>,
        usedBytes: Long,
        capBytes: Long,
    ): AutoDownloadPlan {
        var total = usedBytes
        val allowed = mutableListOf<PinFile>()
        for ((index, file) in candidates.withIndex()) {
            if (total + file.sizeBytes > capBytes) return AutoDownloadPlan(allowed, candidates.drop(index))
            total += file.sizeBytes
            allowed += file
        }
        return AutoDownloadPlan(allowed, emptyList())
    }

    /** True when stored bytes already exceed the cap (the Downloads tab warns even with nothing to download). */
    fun isOverCap(
        usedBytes: Long,
        capBytes: Long,
    ): Boolean = usedBytes > capBytes
}
