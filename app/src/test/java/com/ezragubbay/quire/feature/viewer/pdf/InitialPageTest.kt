package com.ezragubbay.quire.feature.viewer.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialPageTest {
    @Test
    fun requestedPage_isOneBased_andBeatsSaved() = assertEquals(6, RenderWindow.initialPage(7, 2, 20))

    @Test
    fun noRequest_usesSaved() = assertEquals(2, RenderWindow.initialPage(null, 2, 20))

    @Test
    fun nothing_isFirstPage() = assertEquals(0, RenderWindow.initialPage(null, null, 20))

    @Test
    fun requestedPage_clampedToDocument() {
        assertEquals(19, RenderWindow.initialPage(99, null, 20))
        assertEquals(0, RenderWindow.initialPage(0, null, 20))
    }
}
