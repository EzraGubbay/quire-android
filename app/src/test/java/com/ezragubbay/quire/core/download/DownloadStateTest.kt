package com.ezragubbay.quire.core.download

import com.ezragubbay.quire.core.database.DownloadEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadStateTest {
    private fun row(
        state: String,
        progress: Long = 0,
        total: Long = DownloadEntity.UNKNOWN_TOTAL,
        path: String? = null,
        error: String? = null,
    ) = DownloadEntity("f", "c", "pdf", state, progress, total, path, "e", error, 1L)

    @Test
    fun noRowIsIdle() = assertEquals(DownloadState.Idle, (null as DownloadEntity?).toState())

    @Test
    fun queuedIsRunningAtZero() = assertEquals(DownloadState.Running(0, null), row(DownloadEntity.QUEUED).toState())

    @Test
    fun runningCarriesProgressAndTotal() {
        val state = row(DownloadEntity.RUNNING, 50, 200).toState() as DownloadState.Running
        assertEquals(DownloadState.Running(50, 200), state)
        assertEquals(0.25f, state.fraction)
    }

    @Test
    fun unknownTotalHasNoFraction() {
        assertNull((row(DownloadEntity.RUNNING, 50).toState() as DownloadState.Running).fraction)
    }

    @Test
    fun doneCarriesPathAndSize() =
        assertEquals(
            DownloadState.Done("/x/f.pdf", 200),
            row(DownloadEntity.DONE, 200, 200, "/x/f.pdf").toState(),
        )

    @Test
    fun doneWithoutPathIsFailed() =
        assertEquals(DownloadState.Failed("Missing file"), row(DownloadEntity.DONE, 1, 1).toState())

    @Test
    fun failedCarriesReason() =
        assertEquals(DownloadState.Failed("boom"), row(DownloadEntity.FAILED, error = "boom").toState())
}
