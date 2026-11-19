package com.ezragubbay.quire.core.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.ezragubbay.quire.core.database.DownloadEntity
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.network.FakeServerConfig
import com.ezragubbay.quire.core.network.FakeTokenProvider
import com.ezragubbay.quire.core.network.NetworkOptions
import com.ezragubbay.quire.core.network.QuireClientFactory
import com.ezragubbay.quire.core.network.SessionEvents
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
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
import java.io.File
import java.io.IOException
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
class DownloadWorkerTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var db: QuireDatabase
    private lateinit var paths: DownloadPaths
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val bytes = ByteArray(300_000) { (it % 251).toByte() }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        db =
            Room
                .inMemoryDatabaseBuilder(context, QuireDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        paths = DownloadPaths(tmp.root)
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun deps(openSink: ((File, Boolean) -> OutputStream)? = null): DownloadDeps {
        val api =
            QuireClientFactory.contentApi(
                FakeServerConfig(server.url("/").toString()),
                FakeTokenProvider("tok"),
                SessionEvents(),
                NetworkOptions(allowCleartextLocal = true),
            )
        return if (openSink == null) {
            DownloadDeps(db.downloadDao(), api, paths, clock = { 7L })
        } else {
            DownloadDeps(db.downloadDao(), api, paths, { 7L }, openSink)
        }
    }

    private fun worker(
        deps: DownloadDeps,
        attempt: Int = 0,
    ) = TestListenableWorkerBuilder<DownloadWorker>(context)
        .setWorkerFactory(DownloadWorkerFactory(deps))
        .setInputData(
            workDataOf(
                DownloadWorker.KEY_FILE_ID to "f1",
                DownloadWorker.KEY_COURSE to "c1",
                DownloadWorker.KEY_FORMAT to "pdf",
            ),
        ).setRunAttemptCount(attempt)
        .build()

    private suspend fun seed(
        progress: Long = 0,
        etag: String? = null,
        total: Long = DownloadEntity.UNKNOWN_TOTAL,
    ) = db.downloadDao().upsert(
        DownloadEntity("f1", "c1", "pdf", DownloadEntity.QUEUED, progress, total, null, etag, null, 1),
    )

    private fun ok(
        body: ByteArray,
        etag: String = "\"v1\"",
    ) = MockResponse().setBody(Buffer().write(body)).setHeader("ETag", etag)

    private fun partial(
        from: Int,
        etag: String = "\"v1\"",
    ) = MockResponse()
        .setResponseCode(206)
        .setBody(Buffer().write(bytes.copyOfRange(from, bytes.size)))
        .setHeader("ETag", etag)
        .setHeader("Content-Range", "bytes $from-${bytes.size - 1}/${bytes.size}")

    private val finalFile get() = paths.finalFile("c1", "f1", "pdf")
    private val partFile get() = paths.partFile("c1", "f1", "pdf")

    @Test
    fun happyPathStreamsRenamesAndRecordsEtagAndSize() =
        runBlocking {
            seed()
            server.enqueue(ok(bytes))
            assertEquals(Result.success(), worker(deps()).doWork())
            assertArrayEqualsOnDisk(bytes)
            assertFalse(partFile.exists())
            val row = checkNotNull(db.downloadDao().get("f1"))
            assertEquals(DownloadEntity.DONE, row.state)
            assertEquals(bytes.size.toLong(), row.progressBytes)
            assertEquals(bytes.size.toLong(), row.totalBytes)
            assertEquals("\"v1\"", row.etag)
            assertEquals(finalFile.absolutePath, row.localPath)
            val req = server.takeRequest()
            assertEquals("/api/mobile/v1/courses/c1/files/f1/content?format=pdf", req.path)
            assertEquals("Bearer tok", req.getHeader("Authorization"))
            assertNull(req.getHeader("Range"))
        }

    @Test
    fun cutThenResumeContinuesWithRangeAndIfRange() =
        runBlocking {
            seed()
            server.enqueue(ok(bytes).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            assertEquals(Result.retry(), worker(deps()).doWork())
            val have = partFile.length()
            assertTrue("partial bytes kept", have in 1 until bytes.size)
            assertEquals(DownloadEntity.QUEUED, db.downloadDao().get("f1")!!.state)

            server.enqueue(partial(have.toInt()))
            assertEquals(Result.success(), worker(deps(), attempt = 1).doWork())
            assertArrayEqualsOnDisk(bytes)
            server.takeRequest()
            val resume = server.takeRequest()
            assertEquals("bytes=$have-", resume.getHeader("Range"))
            assertEquals("\"v1\"", resume.getHeader("If-Range"))
        }

    @Test
    fun changedEtagRestartsFromZero() =
        runBlocking {
            seed(progress = 1000, etag = "\"old\"")
            partFile.parentFile!!.mkdirs()
            partFile.writeBytes(ByteArray(1000) { 9 })
            // If-Range mismatch: the server ignores Range and returns the whole new body with 200.
            server.enqueue(ok(bytes, etag = "\"new\""))
            assertEquals(Result.success(), worker(deps()).doWork())
            assertArrayEqualsOnDisk(bytes)
            assertEquals("\"new\"", db.downloadDao().get("f1")!!.etag)
            val req = server.takeRequest()
            assertEquals("bytes=1000-", req.getHeader("Range"))
            assertEquals("\"old\"", req.getHeader("If-Range"))
        }

    @Test
    fun unsatisfiableRangeDropsPartialAndRetriesFromZero() =
        runBlocking {
            seed(progress = 1000, etag = "\"old\"")
            partFile.parentFile!!.mkdirs()
            partFile.writeBytes(ByteArray(1000))
            server.enqueue(MockResponse().setResponseCode(416))
            server.enqueue(ok(bytes))
            assertEquals(Result.success(), worker(deps()).doWork())
            assertArrayEqualsOnDisk(bytes)
            server.takeRequest()
            assertNull(server.takeRequest().getHeader("Range"))
        }

    @Test
    fun notFoundFailsPermanently() =
        runBlocking {
            seed()
            server.enqueue(MockResponse().setResponseCode(404))
            assertEquals(Result.failure(), worker(deps()).doWork())
            val row = checkNotNull(db.downloadDao().get("f1"))
            assertEquals(DownloadEntity.FAILED, row.state)
            assertEquals("File not found on the server", row.error)
            assertFalse(finalFile.exists())
        }

    @Test
    fun serverErrorRetriesThenFailsAfterMaxAttempts() =
        runBlocking {
            seed()
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(Result.retry(), worker(deps()).doWork())
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(Result.failure(), worker(deps(), attempt = DownloadWorker.MAX_ATTEMPTS - 1).doWork())
            assertEquals(DownloadEntity.FAILED, db.downloadDao().get("f1")!!.state)
        }

    @Test
    fun diskFullFailsWithoutRetry() =
        runBlocking {
            seed()
            server.enqueue(ok(bytes))
            val full = { _: File, _: Boolean ->
                object : OutputStream() {
                    override fun write(b: Int) = throw IOException("write failed: ENOSPC (No space left on device)")
                }
            }
            assertEquals(Result.failure(), worker(deps(full)).doWork())
            val row = checkNotNull(db.downloadDao().get("f1"))
            assertEquals(DownloadEntity.FAILED, row.state)
            assertEquals("Not enough storage space", row.error)
            assertFalse(finalFile.exists())
        }

    @Test
    fun cancellationKeepsPartialAndEtagForResume() =
        runBlocking {
            seed()
            server.enqueue(ok(bytes))
            lateinit var job: Job
            val cancelling = { f: File, append: Boolean ->
                val real = java.io.FileOutputStream(f, append)
                object : OutputStream() {
                    override fun write(b: Int) = real.write(b)

                    override fun write(
                        b: ByteArray,
                        off: Int,
                        len: Int,
                    ) {
                        real.write(b, off, len)
                        job.cancel()
                    }

                    override fun close() = real.close()
                }
            }
            job = async { worker(deps(cancelling)).doWork() }
            runCatching { job.await() }
            assertTrue(job.isCancelled)
            assertTrue(partFile.length() > 0)
            assertFalse(finalFile.exists())
            val row = checkNotNull(db.downloadDao().get("f1"))
            assertEquals("\"v1\"", row.etag)
            assertTrue(row.state != DownloadEntity.DONE)
        }

    @Test
    fun removedWhileDownloadingLeavesNothingBehind() =
        runBlocking {
            // Row gone before the worker starts (user removed it): no network call, no files.
            assertEquals(Result.failure(), worker(deps()).doWork())
            assertEquals(0, server.requestCount)
            assertFalse(partFile.exists())
        }

    private fun assertArrayEqualsOnDisk(expected: ByteArray) =
        assertTrue("final file content matches", finalFile.readBytes().contentEquals(expected))
}
