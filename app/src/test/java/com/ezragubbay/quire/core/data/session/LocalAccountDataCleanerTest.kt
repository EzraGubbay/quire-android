package com.ezragubbay.quire.core.data.session

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ezragubbay.quire.core.database.DownloadEntity
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.download.DownloadPaths
import com.ezragubbay.quire.core.download.DownloadRepository
import com.ezragubbay.quire.core.download.FakeDownloadScheduler
import com.ezragubbay.quire.feature.viewer.ViewerCache
import com.ezragubbay.quire.sync.OfflineSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LocalAccountDataCleanerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var db: QuireDatabase
    private val scheduler = FakeDownloadScheduler()
    private lateinit var paths: DownloadPaths
    private lateinit var viewer: ViewerCache
    private lateinit var offline: OfflineSettingsStore
    private lateinit var cleaner: LocalAccountDataCleaner
    private lateinit var doneFile: File
    private lateinit var partFile: File
    private lateinit var orphan: File

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    QuireDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        paths = DownloadPaths(tmp.newFolder("files"))
        viewer = ViewerCache(tmp.newFolder("cache"))
        offline =
            OfflineSettingsStore(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("o.preferences_pb") })
        cleaner = LocalAccountDataCleaner(db, DownloadRepository(db.downloadDao(), scheduler, paths), viewer, offline)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private fun seed() =
        runBlocking {
            db.openHelper.writableDatabase.apply {
                execSQL("INSERT INTO courses VALUES ('c', 'id', 'Course', NULL, NULL, 0, 'course', 1)")
                execSQL("INSERT INTO course_counts VALUES ('c', '{}', '{}', 0)")
                execSQL("INSERT INTO sessions VALUES ('c', 'lecture', 1, 'T', NULL, '', NULL)")
                execSQL("INSERT INTO files VALUES ('f1', 'c', 'T', 'pdf', NULL, NULL, NULL, NULL, 1, 1, 0, 0, 'u')")
                execSQL("INSERT INTO notes VALUES ('n1', 'c', 's', 'T', NULL, NULL, 'u')")
                execSQL("INSERT INTO threads VALUES ('t1', 'c', 'T', NULL, 'a', 'b')")
                execSQL("INSERT INTO reading_positions VALUES ('f1', 2, 1)")
            }
            doneFile = paths.finalFile("c", "d", "pdf").apply { parentFile!!.mkdirs() }.also { it.writeText("done") }
            partFile = paths.partFile("c", "r", "pdf").also { it.writeText("part") }
            orphan = File(paths.root, "c/orphan.bin").also { it.writeText("orphan") }
            db.downloadDao().upsert(
                DownloadEntity("d", "c", "pdf", DownloadEntity.DONE, 4, 4, doneFile.path, null, null, 1),
            )
            db.downloadDao().upsert(DownloadEntity("r", "c", "pdf", DownloadEntity.RUNNING, 4, 9, null, "e", null, 1))
            db.downloadDao().upsert(DownloadEntity("x", "c", "pdf", DownloadEntity.FAILED, 0, 9, null, null, "boom", 1))
            viewer.root.mkdirs()
            File(viewer.root, "f1.pdf").writeText("cached")
            offline.addPin("c")
            offline.setCapPaused("c", true)
            offline.startBatch("c", listOf("f1"))
            offline.setCapBytes(777L)
            offline.setNotify(false)
        }

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private val courseCacheTables =
        listOf("courses", "course_counts", "sessions", "files", "notes", "threads", "reading_positions")

    @Test
    fun clearKeepingDownloads_wipesCachesAndUnfinished_butKeepsFinishedDownloadsAndPins() {
        seed()

        runBlocking { cleaner.clear(removeDownloads = false) }

        courseCacheTables.forEach { assertEquals(it, 0, count(it)) }
        // Regression: clearAllTables() would also have emptied this table, orphaning the finished file.
        assertEquals(listOf("d"), runBlocking { db.downloadDao().getAll() }.map { it.fileId })
        assertTrue(doneFile.exists())
        assertFalse(partFile.exists())
        assertTrue(scheduler.cancelledAll)
        assertFalse(
            viewer.root.exists() &&
                viewer.root
                    .listFiles()
                    .orEmpty()
                    .isNotEmpty(),
        )
        val s = runBlocking { offline.current() }
        assertEquals(setOf("c"), s.pinned)
        assertEquals(setOf("c"), s.capPausedSlugs)
    }

    @Test
    fun clearRemovingDownloads_wipesEverythingIncludingOrphansAndPins() {
        seed()

        runBlocking { cleaner.clear(removeDownloads = true) }

        courseCacheTables.forEach { assertEquals(it, 0, count(it)) }
        assertEquals(0, count("downloads"))
        assertFalse(paths.root.exists())
        assertFalse(orphan.exists())
        assertTrue(scheduler.cancelledAll)
        val s = runBlocking { offline.current() }
        assertEquals(emptySet<String>(), s.pinned)
        assertEquals(emptySet<String>(), s.capPausedSlugs)
        assertEquals(777L, s.capBytes)
        assertEquals(false, s.notify)
        // The pending batch is gone: a new one starts from zero.
        runBlocking {
            offline.startBatch("c", listOf("g"))
            assertEquals(1, offline.settle("c", "g", true))
        }
    }

    @Test
    fun bothModes_areIdempotentOnAnEmptyDatabaseWithMissingDirectories() =
        runBlocking {
            cleaner.clear(removeDownloads = false)
            cleaner.clear(removeDownloads = true)
            cleaner.clear(removeDownloads = true)
            assertEquals(0, count("downloads"))
        }

    @Test
    fun clear_runsEveryStepEvenWhenOneFails_thenRethrows() {
        seed()
        // Cancelling work fails (first step); the cache wipe, viewer cache and removal steps must still run.
        val throwing =
            object : com.ezragubbay.quire.core.download.DownloadScheduler by scheduler {
                override fun cancelAll() = error("boom")
            }
        val repo = DownloadRepository(db.downloadDao(), throwing, paths)
        val failing = LocalAccountDataCleaner(db, repo, viewer, offline)

        val thrown = runCatching { runBlocking { failing.clear(removeDownloads = true) } }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
        courseCacheTables.forEach { assertEquals(it, 0, count(it)) }
        assertFalse(viewer.root.exists())
        assertFalse(paths.root.exists())
        assertEquals(emptySet<String>(), runBlocking { offline.current() }.pinned)
    }

    @Test
    fun observeDownloadBytes_countsOnlyFinishedDownloads() {
        seed()

        val bytes = runBlocking { cleaner.observeDownloadBytes().first() }

        assertEquals(4L, bytes) // the DONE row only; the RUNNING row's 4 partial bytes do not count
    }
}
