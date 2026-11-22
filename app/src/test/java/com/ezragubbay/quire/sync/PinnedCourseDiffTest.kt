package com.ezragubbay.quire.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedCourseDiffTest {
    private fun file(
        id: String,
        updatedAt: String = "t1",
        hasPdf: Boolean = true,
    ) = PinFile(id, updatedAt, hasPdf, 10)

    private fun ids(files: List<PinFile>) = files.map { it.fileId }

    @Test
    fun newFile_isAdded() {
        val diff = PinnedCourseDiff.compute(listOf(file("a")), listOf(file("a"), file("b")), listOf(HeldDownload("a")))
        assertEquals(listOf("b"), ids(diff.added))
        assertTrue(diff.changed.isEmpty() && diff.missing.isEmpty() && diff.removed.isEmpty())
    }

    @Test
    fun changedUpdatedAt_isChanged_whenHeld() {
        val diff =
            PinnedCourseDiff.compute(listOf(file("a", "t1")), listOf(file("a", "t2")), listOf(HeldDownload("a")))
        assertEquals(listOf("a"), ids(diff.changed))
        assertTrue(diff.added.isEmpty())
    }

    @Test
    fun changedButNotHeld_isMissing_notChanged() {
        val diff = PinnedCourseDiff.compute(listOf(file("a", "t1")), listOf(file("a", "t2")), emptyList())
        assertEquals(listOf("a"), ids(diff.missing))
        assertTrue(diff.changed.isEmpty())
    }

    @Test
    fun unchangedAndHeld_isIgnored() {
        val diff = PinnedCourseDiff.compute(listOf(file("a")), listOf(file("a")), listOf(HeldDownload("a")))
        assertTrue(diff.toDownload.isEmpty() && diff.removed.isEmpty())
    }

    @Test
    fun unchangedButNeverDownloaded_isMissing() {
        val diff = PinnedCourseDiff.compute(listOf(file("a")), listOf(file("a")), emptyList())
        assertEquals(listOf("a"), ids(diff.missing))
    }

    @Test
    fun failedDownload_isRetriedAsMissing() {
        val diff = PinnedCourseDiff.compute(listOf(file("a")), listOf(file("a")), listOf(HeldDownload("a", true)))
        assertEquals(listOf("a"), ids(diff.missing))
    }

    @Test
    fun fileGoneFromServer_isRemoved_evenWhenOnlyHeld() {
        val diff = PinnedCourseDiff.compute(listOf(file("a")), listOf(file("b")), listOf(HeldDownload("c")))
        assertEquals(setOf("a", "c"), diff.removed)
        assertEquals(listOf("b"), ids(diff.added))
    }

    @Test
    fun fileWithoutPdf_isNeverDownloaded_butNotRemovedWhileOnServer() {
        val diff =
            PinnedCourseDiff.compute(emptyList(), listOf(file("a", hasPdf = false)), listOf(HeldDownload("a")))
        assertTrue(diff.toDownload.isEmpty())
        assertTrue(diff.removed.isEmpty())
    }

    @Test
    fun pinningWithNoPriorSnapshot_downloadsEveryPdfFile_exceptAlreadyHeld() {
        val diff =
            PinnedCourseDiff.compute(
                emptyList(),
                listOf(file("a"), file("b"), file("c", hasPdf = false)),
                listOf(HeldDownload("a")),
            )
        assertEquals(listOf("b"), ids(diff.toDownload))
    }

    @Test
    fun toDownload_keepsAddedThenChangedThenMissing() {
        val diff =
            PinnedCourseDiff.compute(
                listOf(file("c", "t1"), file("m")),
                listOf(file("n"), file("c", "t2"), file("m")),
                listOf(HeldDownload("c")),
            )
        assertEquals(listOf("n", "c", "m"), ids(diff.toDownload))
    }
}
