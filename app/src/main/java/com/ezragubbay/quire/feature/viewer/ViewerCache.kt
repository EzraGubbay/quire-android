package com.ezragubbay.quire.feature.viewer

import java.io.File

/** One file in the viewer cache, as the eviction policy sees it. */
data class CacheEntry(
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

/** Size-capped LRU for the temporary stream-to-cache directory. */
object ViewerCachePolicy {
    const val DEFAULT_CAP_BYTES = 500L * 1024 * 1024

    /** Paths to delete, least recently used first, until the total fits [capBytes]. [keep] is never evicted. */
    fun evictions(
        entries: List<CacheEntry>,
        capBytes: Long = DEFAULT_CAP_BYTES,
        keep: Set<String> = emptySet(),
    ): List<String> {
        var total = entries.sumOf { it.sizeBytes }
        val out = mutableListOf<String>()
        for (e in entries.filter { it.path !in keep }.sortedBy { it.lastModified }) {
            if (total <= capBytes) break
            out += e.path
            total -= e.sizeBytes
        }
        return out
    }
}

/** The evictable cache for files opened but not downloaded: `<cacheDir>/viewer/`. Never a persisted download. */
class ViewerCache(
    cacheDir: File,
    private val capBytes: Long = ViewerCachePolicy.DEFAULT_CAP_BYTES,
) {
    val root: File = File(cacheDir, "viewer")

    /** Cache file for [fileId]; originals keep [title] so the receiving app sees a meaningful name/extension. */
    fun fileFor(
        fileId: String,
        format: String,
        title: String? = null,
    ): File {
        val name =
            if (format == FORMAT_PDF) {
                "${safe(fileId)}.pdf"
            } else {
                "${safe(fileId)}-${safe(title ?: "file")}"
            }
        return File(root, name)
    }

    /** Evicts least-recently-used files beyond the cap, never [keep]. */
    fun trim(keep: File? = null) {
        val files = root.listFiles { f -> f.isFile && !f.name.endsWith(".part") }.orEmpty()
        val doomed =
            ViewerCachePolicy.evictions(
                files.map { CacheEntry(it.path, it.length(), it.lastModified()) },
                capBytes,
                setOfNotNull(keep?.path),
            )
        doomed.forEach { File(it).delete() }
    }

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
