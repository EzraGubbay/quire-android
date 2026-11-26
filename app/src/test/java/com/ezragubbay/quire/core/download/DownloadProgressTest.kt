package com.ezragubbay.quire.core.download

import com.ezragubbay.quire.core.database.DownloadEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadProgressTest {
    private val now = 1_000_000L

    private fun row(
        id: String,
        state: String,
        progress: Long = 0,
        total: Long = 100,
        updatedAt: Long = now,
    ) = DownloadEntity(id, "c", "pdf", state, progress, total, null, null, null, updatedAt)

    @Test
    fun noRowsIsNull() = assertNull(summarize(emptyList(), now))

    @Test
    fun onlyDoneOrFailedIsNull() =
        assertNull(summarize(listOf(row("a", DownloadEntity.DONE), row("b", DownloadEntity.FAILED)), now))

    @Test
    fun onlyQueuedIsNull() = assertNull(summarize(listOf(row("a", DownloadEntity.QUEUED)), now))

    @Test
    fun oneRunningWithKnownTotal() {
        val s = summarize(listOf(row("a", DownloadEntity.RUNNING, progress = 250, total = 1000)), now)!!
        assertEquals(1, s.running)
        assertEquals(250L, s.progressBytes)
        assertEquals(1000L, s.totalBytes)
        assertEquals(25, s.percent)
    }

    @Test
    fun twoRunningAggregate() {
        val s =
            summarize(
                listOf(
                    row("a", DownloadEntity.RUNNING, progress = 100, total = 200),
                    row("b", DownloadEntity.RUNNING, progress = 300, total = 600),
                ),
                now,
            )!!
        assertEquals(2, s.running)
        assertEquals(400L, s.progressBytes)
        assertEquals(800L, s.totalBytes)
        assertEquals(50, s.percent)
    }

    @Test
    fun unknownTotalIsIndeterminate() {
        val s =
            summarize(
                listOf(
                    row("a", DownloadEntity.RUNNING, progress = 100, total = 200),
                    row("b", DownloadEntity.RUNNING, progress = 5, total = DownloadEntity.UNKNOWN_TOTAL),
                ),
                now,
            )!!
        assertNull(s.totalBytes)
        assertNull(s.percent)
    }

    @Test
    fun queuedRowsAreCounted() {
        val s =
            summarize(
                listOf(
                    row("a", DownloadEntity.RUNNING, progress = 1),
                    row("b", DownloadEntity.QUEUED),
                    row("c", DownloadEntity.QUEUED),
                ),
                now,
            )!!
        assertEquals(2, s.queued)
    }

    /** Regression: a worker that was stopped or killed leaves a RUNNING row that must not pin the notification. */
    @Test
    fun staleRunningRowIsNull() {
        val stale = row("a", DownloadEntity.RUNNING, progress = 1, updatedAt = now - STALE_MS - 1)
        assertNull(summarize(listOf(stale), now))
    }

    @Test
    fun staleRowNextToFreshCountsOnlyFresh() {
        val s =
            summarize(
                listOf(
                    row("a", DownloadEntity.RUNNING, progress = 10, total = 100, updatedAt = now - STALE_MS - 1),
                    row("b", DownloadEntity.RUNNING, progress = 50, total = 100),
                ),
                now,
            )!!
        assertEquals(1, s.running)
        assertEquals(50, s.percent)
    }
}
