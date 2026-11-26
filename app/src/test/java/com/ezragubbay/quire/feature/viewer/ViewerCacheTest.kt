package com.ezragubbay.quire.feature.viewer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ViewerCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun put(
        cache: ViewerCache,
        name: String,
        size: Int,
        modified: Long,
        etag: String? = null,
    ): File {
        val f = File(cache.root, name).also { it.parentFile!!.mkdirs() }
        f.writeBytes(ByteArray(size))
        f.setLastModified(modified)
        cache.writeEtag(f, etag)
        return f
    }

    @Test fun trim_ignoresSidecars_andDeletesSidecarWithEvictedFile() {
        val cache = ViewerCache(tmp.root, capBytes = 10)
        // Each file is 6 bytes; with sidecars counted (2 + 6 + ...) the cap would evict differently.
        val old = put(cache, "a.pdf", 6, 1_000L, "\"a\"")
        val fresh = put(cache, "b.pdf", 6, 2_000L, "\"b\"")

        cache.trim(keep = fresh)

        assertFalse(old.exists())
        assertFalse("evicted file takes its sidecar with it", cache.etagFile(old).exists())
        assertTrue(fresh.exists())
        assertTrue(cache.etagFile(fresh).exists())
    }

    @Test fun trim_sidecarBytesDoNotCountTowardCap() {
        val cache = ViewerCache(tmp.root, capBytes = 10)
        val a = put(cache, "a.pdf", 5, 1_000L, "\"a-very-long-etag-value\"")
        val b = put(cache, "b.pdf", 5, 2_000L, "\"another-long-etag-value\"")

        cache.trim()

        assertTrue(a.exists() && b.exists())
    }

    @Test fun trim_removesOrphanSidecars() {
        val cache = ViewerCache(tmp.root)
        val gone = File(cache.root, "gone.pdf").also { it.parentFile!!.mkdirs() }
        cache.writeEtag(gone, "\"x\"")
        assertTrue(cache.etagFile(gone).exists())

        cache.trim()

        assertFalse(cache.etagFile(gone).exists())
    }

    @Test fun etag_roundTrips_andNullOrBlankClears() {
        val cache = ViewerCache(tmp.root)
        val f = put(cache, "a.pdf", 1, 1L)
        assertNull(cache.readEtag(f))

        cache.writeEtag(f, "\"v1\"")
        assertTrue(cache.readEtag(f) == "\"v1\"")

        cache.writeEtag(f, null)
        assertNull(cache.readEtag(f))
        assertFalse(cache.etagFile(f).exists())
    }

    @Test fun clear_removesEverything() {
        val cache = ViewerCache(tmp.root)
        put(cache, "a.pdf", 3, 1L, "\"a\"")
        put(cache, "b.pdf", 3, 2L)

        cache.clear()

        assertFalse(cache.root.exists())
    }
}
