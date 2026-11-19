package com.ezragubbay.quire.feature.courses.files

import com.ezragubbay.quire.core.data.course.CourseFile
import com.ezragubbay.quire.core.designsystem.theme.FileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesTabLogicTest {
    private fun file(
        id: String,
        type: FileType = FileType.LECTURE,
        title: String = id,
        updatedAt: String = "2026-01-01T00:00:00Z",
    ) = CourseFile(
        fileId = id,
        courseSlug = "signals",
        title = title,
        type = type,
        sessionNumber = null,
        sessionTitle = null,
        folder = null,
        pageCount = null,
        sizeBytes = 1,
        hasPdf = true,
        hasOriginal = false,
        hasMarkdown = false,
        updatedAt = updatedAt,
    )

    private val all = FileType.entries.map { file("f-${it.tag}", it) }

    @Test
    fun emptySelectionKeepsEverything() {
        assertEquals(all, all.filterByTypes(emptySet()))
    }

    @Test
    fun eachOfTheSevenTypesFiltersToItself() {
        FileType.entries.forEach { type ->
            assertEquals(listOf("f-${type.tag}"), all.filterByTypes(setOf(type)).map { it.fileId })
        }
    }

    @Test
    fun multiSelectKeepsUnion() {
        val kept = all.filterByTypes(setOf(FileType.EXAM, FileType.BOOK)).map { it.type }
        assertEquals(listOf(FileType.EXAM, FileType.BOOK), kept)
    }

    @Test
    fun filterWithNoMatchYieldsNoGroups() {
        val view = buildFilesView(listOf(file("a", FileType.LECTURE)), setOf(FileType.EXAM), FileSort.COURSE_ORDER)
        assertTrue(view.groups.isEmpty())
        assertEquals(listOf(FileType.LECTURE), view.availableTypes)
    }

    @Test
    fun emptyFilesYieldNoGroupsOrChips() {
        val view = buildFilesView(emptyList(), emptySet(), FileSort.TITLE)
        assertTrue(view.groups.isEmpty())
        assertTrue(view.availableTypes.isEmpty())
    }

    @Test
    fun groupsFollowTypeOrderAndDropEmptyOnes() {
        val shuffled = listOf(file("e", FileType.EXAM), file("l", FileType.LECTURE), file("o", FileType.OTHER))
        val view = buildFilesView(shuffled, emptySet(), FileSort.COURSE_ORDER)
        assertEquals(listOf(FileType.LECTURE, FileType.EXAM, FileType.OTHER), view.groups.map { it.type })
    }

    @Test
    fun courseOrderKeepsIncomingOrder() {
        val files = listOf(file("b"), file("a"), file("c"))
        assertEquals(listOf("b", "a", "c"), files.sortedBy(FileSort.COURSE_ORDER).map { it.fileId })
    }

    @Test
    fun titleSortIsCaseInsensitive() {
        val files = listOf(file("1", title = "beta"), file("2", title = "Alpha"), file("3", title = "gamma"))
        assertEquals(listOf("2", "1", "3"), files.sortedBy(FileSort.TITLE).map { it.fileId })
    }

    @Test
    fun recentSortIsNewestFirst() {
        val files =
            listOf(
                file("old", updatedAt = "2026-01-01T00:00:00Z"),
                file("new", updatedAt = "2026-03-01T00:00:00Z"),
                file("mid", updatedAt = "2026-02-01T00:00:00Z"),
            )
        assertEquals(listOf("new", "mid", "old"), files.sortedBy(FileSort.RECENT).map { it.fileId })
    }

    @Test
    fun sortAppliesWithinEachGroup() {
        val files =
            listOf(file("l2", FileType.LECTURE, "B"), file("e1", FileType.EXAM, "Z"), file("l1", FileType.LECTURE, "A"))
        val view = buildFilesView(files, emptySet(), FileSort.TITLE)
        assertEquals(
            listOf("l1", "l2"),
            view.groups
                .first()
                .files
                .map { it.fileId },
        )
    }

    @Test
    fun parseSelectedTypesIgnoresUnknown() {
        assertEquals(setOf(FileType.EXAM), parseSelectedTypes(listOf("exam", "bogus")))
        assertEquals(emptySet<FileType>(), parseSelectedTypes(null))
    }

    @Test
    fun sortFromKeyFallsBackToCourseOrder() {
        assertEquals(FileSort.TITLE, FileSort.fromKey("title"))
        assertEquals(FileSort.COURSE_ORDER, FileSort.fromKey(null))
        assertEquals(FileSort.COURSE_ORDER, FileSort.fromKey("nope"))
    }

    @Test
    fun formatsDeriveFromFlags() {
        assertEquals(
            listOf(FileFormat.PDF, FileFormat.MARKDOWN),
            file("a").copy(hasMarkdown = true).formats(),
        )
        assertEquals(
            listOf(FileFormat.ORIGINAL),
            file("a").copy(hasPdf = false, hasOriginal = true).formats(),
        )
        assertTrue(file("a").copy(hasPdf = false).formats().isEmpty())
    }

    @Test
    fun locationPrefersSessionThenFolder() {
        assertEquals("Lecture 1", file("a").copy(sessionTitle = "Lecture 1", folder = "f").location())
        assertEquals("Slides", file("a").copy(folder = "Slides").location())
        assertNull(file("a").copy(sessionTitle = " ").location())
    }
}
