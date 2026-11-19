package com.ezragubbay.quire.feature.viewer.pdf

import kotlin.math.ceil

/** Pinch / double-tap zoom for the viewer: [scale] in 1..5 and a horizontal [panX] clamped to the zoomed width. */
data class ZoomState(
    val scale: Float = MIN_SCALE,
    val panX: Float = 0f,
) {
    val isZoomed: Boolean get() = scale > MIN_SCALE

    /** Whole-number render scale, so pages re-render crisply a few times per pinch rather than every frame. */
    val renderBucket: Int get() = ceil(scale.toDouble()).toInt()

    fun pinch(
        factor: Float,
        viewportWidth: Float,
    ): ZoomState = withScale((scale * factor).coerceIn(MIN_SCALE, MAX_SCALE), panX, viewportWidth)

    fun pan(
        dx: Float,
        viewportWidth: Float,
    ): ZoomState = withScale(scale, panX + dx, viewportWidth)

    /** Zoom in to [DOUBLE_TAP_SCALE], or back to 1x when already zoomed. */
    fun doubleTap(viewportWidth: Float): ZoomState =
        if (isZoomed) ZoomState() else withScale(DOUBLE_TAP_SCALE, 0f, viewportWidth)

    private fun withScale(
        newScale: Float,
        newPan: Float,
        viewportWidth: Float,
    ): ZoomState {
        val limit = (newScale - MIN_SCALE) * viewportWidth / 2f
        return ZoomState(newScale, newPan.coerceIn(-limit, limit))
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f
    }
}
