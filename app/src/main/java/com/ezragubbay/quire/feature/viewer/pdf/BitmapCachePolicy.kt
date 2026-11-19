package com.ezragubbay.quire.feature.viewer.pdf

import kotlin.math.floor
import kotlin.math.sqrt

/** Pure sizing rules that keep page bitmaps inside the app's memory class (no OOM on a 300-page deck). */
object BitmapCachePolicy {
    private const val BYTES_PER_MB = 1024 * 1024
    private const val BYTES_PER_PIXEL = 4
    private const val CACHE_FRACTION = 8
    const val MIN_CACHE_BYTES = 4 * BYTES_PER_MB

    /** The bitmap cache budget: an eighth of the memory class, never below [MIN_CACHE_BYTES]. */
    fun cacheBytes(memoryClassMb: Int): Int =
        (memoryClassMb.toLong() * BYTES_PER_MB / CACHE_FRACTION).toInt().coerceAtLeast(MIN_CACHE_BYTES)

    fun bitmapBytes(
        width: Int,
        height: Int,
    ): Int = BYTES_PER_PIXEL * width * height

    /** [width] shrunk, if needed, so a page of height/width ratio [aspect] stays within [maxBytes]. */
    fun fitWidth(
        width: Int,
        aspect: Float,
        maxBytes: Int,
    ): Int {
        if (bitmapBytes(width, (width * aspect).toInt()) <= maxBytes) return width
        return floor(sqrt(maxBytes / (BYTES_PER_PIXEL * aspect))).toInt().coerceAtLeast(1)
    }
}
