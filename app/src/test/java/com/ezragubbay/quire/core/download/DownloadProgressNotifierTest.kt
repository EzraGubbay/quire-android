package com.ezragubbay.quire.core.download

import com.ezragubbay.quire.core.database.DownloadEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadProgressNotifierTest {
    private class FakePoster : DownloadNotificationPoster {
        val events = mutableListOf<String>()
        val shown = mutableListOf<DownloadProgressSummary>()

        override fun show(summary: DownloadProgressSummary) {
            shown += summary
            events += "show"
        }

        override fun clear() {
            events += "clear"
        }
    }

    private fun row(
        state: String,
        progress: Long = 0,
        total: Long = 1000,
    ) = DownloadEntity("f", "c", "pdf", state, progress, total, null, null, null, 0)

    private fun TestScope.fixture(
        initial: List<DownloadEntity> = emptyList(),
    ): Triple<MutableStateFlow<List<DownloadEntity>>, FakePoster, DownloadProgressNotifier> {
        val rows = MutableStateFlow(initial)
        val poster = FakePoster()
        val scope = backgroundScope
        // The clock is frozen at 0 so rows with updatedAt = 0 are always fresh.
        return Triple(rows, poster, DownloadProgressNotifier(rows, poster, scope, clock = { 0L }))
    }

    @Test
    fun startClearsFirst() =
        runTest {
            val (_, poster, notifier) = fixture()
            notifier.start()
            assertEquals("clear", poster.events.first())
        }

    @Test
    fun runningRowShowsSummary() =
        runTest {
            val (rows, poster, notifier) = fixture()
            notifier.start()
            rows.value = listOf(row(DownloadEntity.RUNNING, progress = 500))
            advanceTimeBy(DOWNLOAD_THROTTLE_MS + 1)
            runCurrent()
            assertEquals(50, poster.shown.last().percent)
            assertEquals(1, poster.shown.last().running)
        }

    @Test
    fun burstOfUpdatesInOneWindowPostsOnceWithLatest() =
        runTest {
            val (rows, poster, notifier) = fixture()
            notifier.start()
            runCurrent()
            val before = poster.shown.size
            for (i in 1..20) {
                rows.value = listOf(row(DownloadEntity.RUNNING, progress = i * 10L))
                advanceTimeBy(20)
                runCurrent()
            }
            advanceTimeBy(DOWNLOAD_THROTTLE_MS)
            runCurrent()
            assertTrue(poster.shown.size - before <= 1)
            assertEquals(20, poster.shown.last().percent)
            rows.value = listOf(row(DownloadEntity.RUNNING, progress = 900))
            advanceTimeBy(DOWNLOAD_THROTTLE_MS + 1)
            runCurrent()
            assertEquals(90, poster.shown.last().percent)
        }

    @Test
    fun samePercentDoesNotRepost() =
        runTest {
            val (rows, poster, notifier) = fixture()
            notifier.start()
            rows.value = listOf(row(DownloadEntity.RUNNING, progress = 500))
            advanceTimeBy(DOWNLOAD_THROTTLE_MS + 1)
            runCurrent()
            val count = poster.shown.size
            // 501/1000 is still 50 percent, but the byte count differs: only the percent is observed.
            rows.value = listOf(row(DownloadEntity.RUNNING, progress = 501))
            advanceTimeBy(DOWNLOAD_THROTTLE_MS * 3)
            runCurrent()
            assertEquals(count, poster.shown.size)
        }

    @Test
    fun liveDownloadWithUnchangedPercentReassertsItselfBeforeTheNotificationTimeout() =
        runTest {
            var now = 0L
            val rows = MutableStateFlow(listOf(row(DownloadEntity.RUNNING, progress = 500)))
            val poster = FakePoster()
            DownloadProgressNotifier(rows, poster, backgroundScope, clock = { now }).start()
            advanceTimeBy(DOWNLOAD_THROTTLE_MS + 1)
            runCurrent()
            val before = poster.shown.size

            // Same percent, but the worker is alive (the row is rewritten) and half the timeout has passed.
            now = DOWNLOAD_STALE_MS / 2 + 1
            rows.value = listOf(row(DownloadEntity.RUNNING, progress = 501).copy(updatedAt = now))
            advanceTimeBy(DOWNLOAD_THROTTLE_MS + 1)
            runCurrent()

            assertEquals(before + 1, poster.shown.size)
        }

    @Test
    fun doneClears() =
        runTest {
            val (rows, poster, notifier) = fixture(listOf(row(DownloadEntity.RUNNING, progress = 1)))
            notifier.start()
            advanceTimeBy(DOWNLOAD_THROTTLE_MS + 1)
            runCurrent()
            rows.value = listOf(row(DownloadEntity.DONE, progress = 1000))
            advanceTimeBy(DOWNLOAD_THROTTLE_MS + 1)
            runCurrent()
            assertEquals("clear", poster.events.last())
        }

    @Test
    fun removedRowAndRetryBackoffClear() =
        runTest {
            val (rows, poster, notifier) = fixture(listOf(row(DownloadEntity.RUNNING, progress = 1)))
            notifier.start()
            advanceTimeBy(DOWNLOAD_THROTTLE_MS + 1)
            runCurrent()
            assertEquals("show", poster.events.last())
            rows.value = emptyList()
            advanceTimeBy(DOWNLOAD_THROTTLE_MS + 1)
            runCurrent()
            assertEquals("clear", poster.events.last())

            rows.value = listOf(row(DownloadEntity.RUNNING, progress = 1))
            advanceTimeBy(DOWNLOAD_THROTTLE_MS + 1)
            runCurrent()
            assertEquals("show", poster.events.last())
            rows.value = listOf(row(DownloadEntity.QUEUED))
            advanceTimeBy(DOWNLOAD_THROTTLE_MS + 1)
            runCurrent()
            assertEquals("clear", poster.events.last())
        }

    @Test
    fun startTwiceCollectsOnce() =
        runTest {
            val (rows, poster, notifier) = fixture()
            notifier.start()
            notifier.start()
            rows.value = listOf(row(DownloadEntity.RUNNING, progress = 500))
            advanceTimeBy(DOWNLOAD_THROTTLE_MS + 1)
            runCurrent()
            assertEquals(1, poster.events.count { it == "clear" })
            assertEquals(1, poster.shown.size)
        }
}
