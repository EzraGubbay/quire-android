package com.ezragubbay.quire.core.designsystem.icon

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.VectorPath
import com.ezragubbay.quire.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveBoxArrowDownTest {
    private val icon = QuireIcons.ArchiveBoxArrowDown

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
