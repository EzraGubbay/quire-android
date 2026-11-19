package com.ezragubbay.quire.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemesterKeyTest {
    @Test
    fun ordersByYearThenSeason() {
        val keys = listOf("Winter 2025", "Spring 2026", "Winter 2026", "Summer 2026").map(::semesterKey)
        assertTrue(keys[0] < keys[2])
        assertTrue(keys[2] < keys[1])
        assertTrue(keys[1] < keys[3])
    }

    @Test
    fun unparseableOrMissingSortsLast() {
        assertEquals(0, semesterKey(null))
        assertEquals(0, semesterKey("whenever"))
    }
}
