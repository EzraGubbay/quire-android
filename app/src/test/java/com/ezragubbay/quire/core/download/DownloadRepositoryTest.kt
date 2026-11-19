package com.ezragubbay.quire.core.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.ezragubbay.quire.core.database.DownloadEntity
import com.ezragubbay.quire.core.database.QuireDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: QuireDatabase
    private val scheduler = FakeDownloadScheduler()
    private lateinit var paths: DownloadPaths
    private lateinit var repo: DownloadRepository

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    QuireDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        paths = DownloadPaths(tmp.root)
        repo = DownloadRepository(db.downloadDao(), scheduler, paths) { 42L }
    }

    private fun row(
        id: String,
        state: String,
        progress: Long,
        total: Long,
        path: String? = null,
    ) = DownloadEntity(id, "c", "pdf", state, progress, total, path, "e", null, 1)

    @After
    fun tearDown() = db.close()

    @Test
    fun unknownFileIsIdle() = runBlocking { assertEquals(DownloadState.Idle, repo.observe("f1").first()) }

    @Test
    fun enqueueWritesQueuedRowAndSchedulesWork() =
        runBlocking {
            repo.enqueue("c", "f1", "pdf", wifiOnly = true)
            assertEquals(DownloadState.Running(0, null), repo.observe("f1").first())
            assertEquals(listOf(listOf<Any>("c", "f1", "pdf", true)), scheduler.enqueued)
        }

    @Test
    fun observeEmitsTransitionsFromRoom() =
        runBlocking {
            repo.observe("f1").test {
                assertEquals(DownloadState.Idle, awaitItem())
                repo.enqueue("c", "f1", "pdf")
                assertEquals(DownloadState.Running(0, null), awaitItem())
                db.downloadDao().update(
                    DownloadEntity("f1", "c", "pdf", DownloadEntity.RUNNING, 10, 100, null, "e", null, 1),
                )
                assertEquals(DownloadState.Running(10, 100), awaitItem())
                db.downloadDao().update(
                    DownloadEntity("f1", "c", "pdf", DownloadEntity.DONE, 100, 100, "/p", "e", null, 2),
                )
                assertEquals(DownloadState.Done("/p", 100), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun enqueueOnDoneDownloadIsNoOp() =
        runBlocking {
            db.downloadDao().upsert(DownloadEntity("f1", "c", "pdf", DownloadEntity.DONE, 9, 9, "/p", "e", null, 1))
            repo.enqueue("c", "f1", "pdf")
            assertTrue(scheduler.enqueued.isEmpty())
            assertEquals(DownloadState.Done("/p", 9), repo.observe("f1").first())
        }

    @Test
    fun retryAfterFailureKeepsPartialProgressAndEtag() =
        runBlocking {
            db.downloadDao().upsert(DownloadEntity("f1", "c", "pdf", DownloadEntity.FAILED, 30, 100, null, "e", "x", 1))
            repo.enqueue("c", "f1", "pdf")
            val row = checkNotNull(db.downloadDao().get("f1"))
            assertEquals(DownloadEntity.QUEUED, row.state)
            assertEquals(30L, row.progressBytes)
            assertEquals("e", row.etag)
            assertNull(row.error)
        }

    @Test
    fun switchingFormatDropsOldBytes() =
        runBlocking {
            val old = paths.finalFile("c", "f1", "pdf").also { it.parentFile!!.mkdirs() }
            old.writeText("old")
            db.downloadDao().upsert(DownloadEntity("f1", "c", "pdf", DownloadEntity.DONE, 3, 3, old.path, "e", null, 1))
            repo.enqueue("c", "f1", "markdown")
            assertFalse(old.exists())
            assertNull(db.downloadDao().get("f1")!!.etag)
        }

    @Test
    fun removeCancelsAndDeletesRowAndBothFiles() =
        runBlocking {
            val final = paths.finalFile("c", "f1", "pdf").also { it.parentFile!!.mkdirs() }
            final.writeText("x")
            val part = paths.partFile("c", "f1", "pdf").also { it.writeText("y") }
            db.downloadDao().upsert(
                DownloadEntity("f1", "c", "pdf", DownloadEntity.DONE, 1, 1, final.path, "e", null, 1),
            )
            repo.remove("f1")
            assertEquals(listOf("f1"), scheduler.cancelled)
            assertEquals(DownloadState.Idle, repo.observe("f1").first())
            assertFalse(final.exists())
            assertFalse(part.exists())
        }

    @Test
    fun storageSumsAllRowsAndObserveAllListsThem() =
        runBlocking {
            db.downloadDao().upsert(DownloadEntity("a", "c1", "pdf", DownloadEntity.DONE, 100, 100, "/a", "e", null, 1))
            db.downloadDao().upsert(
                DownloadEntity("b", "c2", "pdf", DownloadEntity.RUNNING, 50, 90, null, "e", null, 2),
            )
            assertEquals(150L, repo.storageBytes())
            assertEquals(150L, repo.observeStorageBytes().first())
            val items = repo.observeAll().first()
            assertEquals(setOf("a", "b"), items.map { it.fileId }.toSet())
            assertEquals(DownloadState.Running(50, 90), items.first { it.fileId == "b" }.state)
        }

    @Test
    fun removeAllClearsEverything() =
        runBlocking {
            db.downloadDao().upsert(DownloadEntity("a", "c1", "pdf", DownloadEntity.DONE, 100, 100, "/a", "e", null, 1))
            repo.removeAll()
            assertTrue(scheduler.cancelledAll)
            assertEquals(0L, repo.storageBytes())
            assertTrue(repo.observeAll().first().isEmpty())
        }

    @Test
    fun localFileOnlyWhenDoneAndOnDisk() =
        runBlocking {
            val f = tmp.newFile("x.pdf")
            db.downloadDao().upsert(DownloadEntity("a", "c", "pdf", DownloadEntity.DONE, 1, 1, f.path, "e", null, 1))
            assertEquals(f, repo.localFile("a"))
            f.delete()
            assertNull(repo.localFile("a"))
            assertNull(repo.localFile("missing"))
        }
}
