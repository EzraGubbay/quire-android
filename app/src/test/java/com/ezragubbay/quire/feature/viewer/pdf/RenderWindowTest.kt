package com.ezragubbay.quire.feature.viewer.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderWindowTest {
    @Test
    fun windowExpandsAroundVisiblePages() {
        assertEquals(3..9, RenderWindow.keep(firstVisible = 5, lastVisible = 7, pageCount = 100, margin = 2))
    }

    @Test
    fun windowClampsToDocumentBounds() {
        assertEquals(0..3, RenderWindow.keep(firstVisible = 0, lastVisible = 1, pageCount = 100, margin = 2))
        assertEquals(96..99, RenderWindow.keep(firstVisible = 98, lastVisible = 99, pageCount = 100, margin = 2))
    }

    @Test
    fun emptyDocumentHasEmptyWindow() {
        assertTrue(RenderWindow.keep(0, 0, pageCount = 0, margin = 2).isEmpty())
    }

    @Test
    fun containsChecksMembership() {
        val window = RenderWindow.keep(5, 5, 20, 1)
        assertTrue(4 in window && 6 in window)
        assertTrue(8 !in window)
    }

    @Test
    fun clampPageRestoresSavedPageWithinBounds() {
        assertEquals(0, RenderWindow.clampPage(saved = -3, pageCount = 10))
        assertEquals(9, RenderWindow.clampPage(saved = 50, pageCount = 10))
        assertEquals(4, RenderWindow.clampPage(saved = 4, pageCount = 10))
        assertEquals(0, RenderWindow.clampPage(saved = 4, pageCount = 0))
    }
}
