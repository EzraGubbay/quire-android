package com.ezragubbay.quire.core.designsystem.icon

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.addPathNodes
import com.ezragubbay.quire.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveBoxArrowDownTest {
    private val icon = QuireIcons.ArchiveBoxArrowDown

    /** The `d` attribute of Heroicons v2 outline `archive-box-arrow-down.svg`, verbatim (split for line length). */
    private val canonicalPathData =
        "m20.25 7.5-.625 10.632a2.25 2.25 0 0 1-2.247 2.118H6.622a2.25 2.25 0 0 1-2.247-2.118L3.75 7.5" +
            "m8.25 3v6.75m0 0-3-3m3 3 3-3M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5" +
            "c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125Z"

    @Test
    fun pathMatchesTheHeroiconsSvg() {
        val path = icon.root.filterIsInstance<VectorPath>().single()
        assertEquals(addPathNodes(canonicalPathData), path.pathData)
    }

    @Test
    fun isA24dpOutlineIcon() {
        assertEquals(24f, icon.defaultWidth.value)
        assertEquals(24f, icon.defaultHeight.value)
        assertEquals(24f, icon.viewportWidth)
        assertEquals(24f, icon.viewportHeight)
    }

    @Test
    fun drawsOneRoundStrokeWithoutFill() {
        val paths = icon.root.filterIsInstance<VectorPath>()
        assertEquals(1, paths.size)
        val path = paths.single()
        assertNull(path.fill)
        assertTrue(path.stroke != null)
        assertEquals(1.5f, path.strokeLineWidth)
        assertEquals(StrokeCap.Round, path.strokeLineCap)
        assertEquals(StrokeJoin.Round, path.strokeLineJoin)
        assertTrue(path.pathData.isNotEmpty())
    }

    @Test
    fun downloadsTabUsesIt() {
        assertSame(icon, TopLevelDestination.Downloads.icon)
    }
}
