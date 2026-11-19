package com.ezragubbay.quire.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuireSpacingTest {
    @Test
    fun scaleIsStrictlyIncreasingOnA4dpGrid() {
        val s = QuireSpacing()
        val steps = listOf(s.xxs, s.xs, s.sm, s.md, s.lg, s.xl, s.xxl, s.xxxl).map { it.value }
        assertTrue(steps.zipWithNext().all { (a, b) -> a < b })
        assertTrue(steps.all { it % 4f == 0f })
        assertEquals(16f, s.md.value, 0f)
    }
}
