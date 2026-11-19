package com.ezragubbay.quire.feature.courses.list

import org.junit.Assert.assertEquals
import org.junit.Test

class SemesterOrderTest {
    @Test
    fun mostRecentSemesterFirst() {
        val sorted = listOf("Winter 2025", "Spring 2026", "Winter 2026", "Fall 2025").sortedWith(SemesterOrder)
        assertEquals(listOf("Spring 2026", "Winter 2026", "Fall 2025", "Winter 2025"), sorted)
    }

    @Test
    fun unknownFormatsAndNullSortLast() {
        val sorted = listOf(null, "Winter 2025", "Someday", "Spring 2026").sortedWith(SemesterOrder)
        assertEquals(listOf("Spring 2026", "Winter 2025", "Someday", null), sorted)
    }

    @Test
    fun slugifiesLabelsForTags() {
        assertEquals("winter-2026", semesterSlug("Winter 2026"))
        assertEquals("other", semesterSlug(null))
        assertEquals("other", semesterSlug("  "))
    }
}
