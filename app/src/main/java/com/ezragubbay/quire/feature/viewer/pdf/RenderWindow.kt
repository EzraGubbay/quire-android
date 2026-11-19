package com.ezragubbay.quire.feature.viewer.pdf

/** Pure page-window math for the PDF viewer. */
object RenderWindow {
    /** The pages worth keeping rendered: the visible range widened by [margin] each side, clamped to the document. */
    fun keep(
        firstVisible: Int,
        lastVisible: Int,
        pageCount: Int,
        margin: Int,
    ): IntRange {
        if (pageCount <= 0) return IntRange.EMPTY
        val first = (firstVisible - margin).coerceIn(0, pageCount - 1)
        val last = (lastVisible + margin).coerceIn(first, pageCount - 1)
        return first..last
    }

    /** A saved 0-based page forced into `0 until pageCount` (0 for an empty document). */
    fun clampPage(
        saved: Int,
        pageCount: Int,
    ): Int = if (pageCount <= 0) 0 else saved.coerceIn(0, pageCount - 1)
}
