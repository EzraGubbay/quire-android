package com.ezragubbay.quire.feature.viewer.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomStateTest {
    @Test
    fun startsAtOneXWithNoPan() {
        val z = ZoomState()
        assertEquals(1f, z.scale, 0f)
        assertEquals(0f, z.panX, 0f)
        assertFalse(z.isZoomed)
    }

    @Test
    fun pinchIsClampedToOneToFiveX() {
        assertEquals(5f, ZoomState().pinch(factor = 100f, viewportWidth = 1000f).scale, 0f)
        assertEquals(1f, ZoomState(scale = 2f).pinch(factor = 0.01f, viewportWidth = 1000f).scale, 0f)
        assertEquals(3f, ZoomState(scale = 2f).pinch(factor = 1.5f, viewportWidth = 1000f).scale, 0.001f)
    }

    @Test
    fun doubleTapZoomsInThenResets() {
        val zoomed = ZoomState().doubleTap(viewportWidth = 1000f)
        assertEquals(ZoomState.DOUBLE_TAP_SCALE, zoomed.scale, 0f)
        assertTrue(zoomed.isZoomed)
        val reset = zoomed.doubleTap(viewportWidth = 1000f)
        assertEquals(1f, reset.scale, 0f)
        assertEquals(0f, reset.panX, 0f)
    }

    @Test
    fun panIsClampedToTheZoomedContent() {
        val z = ZoomState(scale = 2f).pan(dx = 9_999f, viewportWidth = 1000f)
        assertEquals(500f, z.panX, 0f) // (scale - 1) * width / 2
        assertEquals(-500f, z.pan(dx = -9_999f, viewportWidth = 1000f).panX, 0f)
    }

    @Test
    fun panIsIgnoredAtOneX() {
        assertEquals(0f, ZoomState().pan(dx = 200f, viewportWidth = 1000f).panX, 0f)
    }

    @Test
    fun zoomingOutReclampsPan() {
        val z = ZoomState(scale = 3f, panX = 1000f).pinch(factor = 0.5f, viewportWidth = 1000f)
        assertEquals(1.5f, z.scale, 0f)
        assertEquals(250f, z.panX, 0f)
    }

    @Test
    fun renderScaleBucketsToWholeSteps() {
        assertEquals(1, ZoomState(scale = 1f).renderBucket)
        assertEquals(2, ZoomState(scale = 1.2f).renderBucket)
        assertEquals(3, ZoomState(scale = 2.5f).renderBucket)
        assertEquals(5, ZoomState(scale = 5f).renderBucket)
    }
}
