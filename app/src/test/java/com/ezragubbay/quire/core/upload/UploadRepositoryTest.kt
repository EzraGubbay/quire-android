package com.ezragubbay.quire.core.upload

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.database.UploadEntity
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
import java.io.ByteArrayInputStream
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
class UploadRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: QuireDatabase
    private lateinit var paths: UploadPaths
    private val scheduler = FakeUploadScheduler()
    private val picked = mutableMapOf<Uri, Pair<UriMeta, ByteArray>>()
    private var ids = 0

    private val source =
        object : UriSource {
            override fun query(uri: Uri) = picked.getValue(uri).first

            override fun open(uri: Uri): InputStream = ByteArrayInputStream(picked.getValue(uri).second)
        }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, QuireDatabase::class.java).allowMainThreadQueries().build()
        paths = UploadPaths(tmp.root)
    }

    @After
    fun tearDown() = db.close()

    private fun repo(): UploadRepository {
        var next = 0
        return UploadRepository(
            db.uploadDao(),
            scheduler,
            paths,
            UploadStager(source, paths) { "s${ids++}" },
            clock = { 100L + next++ },
            newId = { "fresh${ids++}" },
        )
    }

    private fun pick(
        name: String,
        bytes: ByteArray,
        mime: String? = null,
    ): Uri {
        val uri = Uri.parse("content://t/${picked.size}")
        picked[uri] = UriMeta(name, bytes.size.toLong(), mime) to bytes
        return uri
    }

    private val pdf = "%PDF-1.7\n".toByteArray()
    private val md = "# hi".toByteArray()

    @Test
    fun enqueue_schedulesOneUniqueWorkPerFile_andInsertsQueuedRows() =
        runBlocking {
            val r = repo()
            val result = r.enqueue("c1", UploadTab.FILES, listOf(pick("a.pdf", pdf), pick("b.md", md)))

            assertEquals(listOf("s0", "s1"), result.enqueuedIds)
            assertEquals(listOf("s0", "s1"), scheduler.enqueued)
            val rows = db.uploadDao().getAll().sortedBy { it.id }
            assertEquals(listOf("document", "document"), rows.map { it.target })
            assertEquals(listOf("pdf", "markdown"), rows.map { it.format })
            assertTrue(rows.all { it.state == UploadEntity.QUEUED && it.serverUploadId == null && !it.serverFailed })
        }

    @Test
    fun enqueue_returnsRejections_andInsertsNothingForThem() =
        runBlocking {
            val result = repo().enqueue("c1", UploadTab.FILES, listOf(pick("x.docx", ByteArray(5))))

            assertEquals(listOf(UploadRejected("x.docx", UploadRejection.UNSUPPORTED)), result.rejections)
            assertTrue(result.enqueuedIds.isEmpty())
            assertTrue(db.uploadDao().getAll().isEmpty())
            assertTrue(scheduler.enqueued.isEmpty())
        }

    @Test
    fun pdfOnNotes_isQueuedAsDocument_andReportedAsSavedToDocuments() =
        runBlocking {
            val result = repo().enqueue("c1", UploadTab.NOTES, listOf(pick("p.pdf", pdf), pick("n.md", md)))

            assertEquals(listOf("p.pdf"), result.savedToDocuments)
            val rows = db.uploadDao().getAll().associate { it.displayName to it.target }
            assertEquals(mapOf("p.pdf" to "document", "n.md" to "note"), rows)
        }

    @Test
    fun retryAfterTransientFailure_keepsTheSameId_andReschedules() =
        runBlocking {
            val r = repo()
            r.enqueue("c1", UploadTab.FILES, listOf(pick("a.pdf", pdf)))
            val row = db.uploadDao().get("s0")!!
            db.uploadDao().update(row.copy(state = UploadEntity.FAILED, error = "Network error", serverUploadId = null))
            scheduler.enqueued.clear()

            r.retry("s0")

            val after = db.uploadDao().get("s0")!!
            assertEquals(UploadEntity.QUEUED, after.state)
            assertNull(after.error)
            assertEquals(listOf("s0"), scheduler.enqueued)
        }

    @Test
    fun retryAfterServerFailure_usesANewId_andKeepsTheStagedFile() =
        runBlocking {
            val r = repo()
            r.enqueue("c1", UploadTab.FILES, listOf(pick("a.pdf", pdf)))
            val row = db.uploadDao().get("s0")!!
            db.uploadDao().update(
                row.copy(state = UploadEntity.FAILED, serverUploadId = "srv", serverFailed = true, error = "bad"),
            )
            scheduler.enqueued.clear()

            r.retry("s0")

            assertNull(db.uploadDao().get("s0"))
            val fresh = db.uploadDao().getAll().single()
            assertEquals("fresh1", fresh.id)
            assertEquals(UploadEntity.QUEUED, fresh.state)
            assertNull(fresh.serverUploadId)
            assertFalse(fresh.serverFailed)
            assertEquals(row.localPath, fresh.localPath)
            assertTrue(java.io.File(fresh.localPath).exists())
            assertEquals(listOf("fresh1"), scheduler.enqueued)
        }

    @Test
    fun retryOnANonFailedRow_isANoOp() =
        runBlocking {
            val r = repo()
            r.enqueue("c1", UploadTab.FILES, listOf(pick("a.pdf", pdf)))
            scheduler.enqueued.clear()

            r.retry("s0")

            assertTrue(scheduler.enqueued.isEmpty())
        }

    @Test
    fun cancel_removesRowFileAndWork() =
        runBlocking {
            val r = repo()
            r.enqueue("c1", UploadTab.FILES, listOf(pick("a.pdf", pdf)))
            val file = java.io.File(db.uploadDao().get("s0")!!.localPath)

            r.cancel("s0")

            assertNull(db.uploadDao().get("s0"))
            assertFalse(file.exists())
            assertEquals(listOf("s0"), scheduler.cancelled)
        }

    @Test
    fun clearAll_wipesEverything() =
        runBlocking {
            val r = repo()
            r.enqueue("c1", UploadTab.FILES, listOf(pick("a.pdf", pdf)))
            r.enqueue("c2", UploadTab.FILES, listOf(pick("b.md", md)))

            r.clearAll()

            assertTrue(db.uploadDao().getAll().isEmpty())
            assertTrue(scheduler.cancelledAll)
            assertFalse(paths.root.exists())
        }

    @Test
    fun observe_filtersByCourse_andHidesDoneRows() =
        runBlocking {
            val r = repo()
            r.enqueue("c1", UploadTab.FILES, listOf(pick("a.pdf", pdf), pick("b.pdf", pdf)))
            r.enqueue("c2", UploadTab.FILES, listOf(pick("c.pdf", pdf)))
            val done = db.uploadDao().get("s1")!!
            db.uploadDao().update(done.copy(state = UploadEntity.DONE))

            val items = r.observe("c1").first()

            assertEquals(listOf("a.pdf"), items.map { it.displayName })
            assertEquals(UploadItem.State.QUEUED, items.single().state)
        }
}
