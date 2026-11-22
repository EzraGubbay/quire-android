package com.ezragubbay.quire.sync

/** What auto-download needs to know about one cached file. [updatedAt] is the server's change marker. */
data class PinFile(
    val fileId: String,
    val updatedAt: String,
    val hasPdf: Boolean,
    val sizeBytes: Long,
)

/** A download row the device holds for a pinned course's file; [failed] rows are retried like missing ones. */
data class HeldDownload(
    val fileId: String,
    val failed: Boolean = false,
)

/**
 * What a sync means for one pinned course. [added], [changed] and [missing] are PDF-renderable files to (re)download;
 * [removed] are file ids the server no longer lists, whose local copies are deleted.
 */
data class PinnedDiff(
    val added: List<PinFile>,
    val changed: List<PinFile>,
    val missing: List<PinFile>,
    val removed: Set<String>,
) {
    val toDownload: List<PinFile> get() = added + changed + missing
}

/** Pure: compares a course's files before and after a refresh against what is already downloaded. */
object PinnedCourseDiff {
    fun compute(
        before: List<PinFile>,
        after: List<PinFile>,
        held: List<HeldDownload>,
    ): PinnedDiff {
        val previous = before.filter { it.hasPdf }.associateBy { it.fileId }
        val heldById = held.associateBy { it.fileId }
        val added = mutableListOf<PinFile>()
        val changed = mutableListOf<PinFile>()
        val missing = mutableListOf<PinFile>()
        for (file in after.filter { it.hasPdf }) {
            val prior = previous[file.fileId]
            val download = heldById[file.fileId]
            val usable = download != null && !download.failed
            when {
                prior == null -> if (!usable) added += file
                prior.updatedAt != file.updatedAt && download != null -> changed += file
                !usable -> missing += file
            }
        }
        val onServer = after.map { it.fileId }.toSet()
        return PinnedDiff(added, changed, missing, previous.keys + heldById.keys - onServer)
    }
}
