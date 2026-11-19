package com.ezragubbay.quire.feature.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerCachePolicyTest {
    private fun e(
        p: String,
        size: Long,
        t: Long,
    ) = CacheEntry(p, size, t)

    @Test fun underCap_evictsNothing() =
        assertEquals(emptyList<String>(), ViewerCachePolicy.evictions(listOf(e("a", 5, 1)), 10))

    @Test fun overCap_evictsOldestFirstUntilFits() =
        assertEquals(
            listOf("a", "b"),
            ViewerCachePolicy.evictions(listOf(e("c", 4, 3), e("a", 4, 1), e("b", 4, 2)), 4),
        )

    @Test fun keep_isNeverEvicted() =
        assertEquals(
            listOf("b"),
            ViewerCachePolicy.evictions(listOf(e("a", 6, 1), e("b", 6, 2)), 6, keep = setOf("a")),
        )

    @Test fun defaultCap_is500MB() = assertEquals(500L * 1024 * 1024, ViewerCachePolicy.DEFAULT_CAP_BYTES)
}
