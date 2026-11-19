package com.ezragubbay.quire.feature.viewer.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapCachePolicyTest {
    @Test
    fun cacheTakesAEighthOfTheMemoryClass() {
        assertEquals(32L * 1024 * 1024 / 8 * 1, BitmapCachePolicy.cacheBytes(memoryClassMb = 32).toLong())
        assertEquals(256L * 1024 * 1024 / 8, BitmapCachePolicy.cacheBytes(memoryClassMb = 256).toLong())
    }

    @Test
    fun cacheHasAFloor() {
        assertEquals(BitmapCachePolicy.MIN_CACHE_BYTES, BitmapCachePolicy.cacheBytes(memoryClassMb = 8))
    }

    @Test
    fun bitmapBytesIsFourBytesPerPixel() {
        assertEquals(4 * 100 * 200, BitmapCachePolicy.bitmapBytes(100, 200))
    }

    @Test
    fun widthIsKeptWhenThePageFits() {
        assertEquals(1080, BitmapCachePolicy.fitWidth(1080, aspect = 1.4f, maxBytes = 64 * 1024 * 1024))
    }

    @Test
    fun widthShrinksSoOneBitmapNeverExceedsTheBudget() {
        val width = BitmapCachePolicy.fitWidth(10_000, aspect = 1.5f, maxBytes = 8 * 1024 * 1024)
        val height = (width * 1.5f).toInt()
        assertTrue(BitmapCachePolicy.bitmapBytes(width, height) <= 8 * 1024 * 1024)
        assertTrue(width in 1..9_999)
    }
}
