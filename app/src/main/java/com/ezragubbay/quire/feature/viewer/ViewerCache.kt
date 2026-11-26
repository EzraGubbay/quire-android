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

    /** The sidecar holding the `ETag` validator of [target], next to it so the two live and die together. */
    fun etagFile(target: File) = File(target.path + ETAG_SUFFIX)

    /** The stored `ETag` of [target] (quotes kept verbatim), or null when there is no usable validator. */
    fun readEtag(target: File): String? =
        runCatching { etagFile(target).takeIf { it.isFile }?.readText()?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    /** Stores [etag] as the validator of [target]; null deletes the sidecar. */
    fun writeEtag(
        target: File,
        etag: String?,
    ) {
        val sidecar = etagFile(target)
        if (etag.isNullOrBlank()) sidecar.delete() else sidecar.writeText(etag.trim())
    }

    /**
     * Evicts least-recently-used files beyond the cap, never [keep]. Sidecars do not count toward the cap: they go
     * with their file, and a sidecar whose file is gone is removed.
     */
    fun trim(keep: File? = null) {
        val all = root.listFiles { f -> f.isFile && !f.name.endsWith(PART_SUFFIX) }.orEmpty()
        val (sidecars, files) = all.partition { it.name.endsWith(ETAG_SUFFIX) }
        val doomed =
            ViewerCachePolicy.evictions(
                files.map { CacheEntry(it.path, it.length(), it.lastModified()) },
                capBytes,
                setOfNotNull(keep?.path),
            )
        doomed.forEach {
            val file = File(it)
            file.delete()
            etagFile(file).delete()
        }
        sidecars.filter { !File(it.path.removeSuffix(ETAG_SUFFIX)).exists() }.forEach { it.delete() }
    }

    /** Deletes everything in the cache (for sign-out: the cache is not account-scoped). */
    fun clear() {
        root.deleteRecursively()
    }

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        const val ETAG_SUFFIX = ".etag"
        const val PART_SUFFIX = ".part"
    }
}
