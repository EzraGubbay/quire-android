package com.ezragubbay.quire.core.upload

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.database.UploadEntity
import com.ezragubbay.quire.core.network.FakeServerConfig
import com.ezragubbay.quire.core.network.FakeTokenProvider
import com.ezragubbay.quire.core.network.NetworkOptions
import com.ezragubbay.quire.core.network.QuireClientFactory
import com.ezragubbay.quire.core.network.SessionEvents
import com.ezragubbay.quire.core.network.UploadApi
import com.ezragubbay.quire.core.network.UploadJobDto
import com.ezragubbay.quire.core.network.fixture
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
import retrofit2.Response
import java.io.IOException

@OptIn(DelicateCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UploadWorkerTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var db: QuireDatabase
    private lateinit var paths: UploadPaths
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pdfBytes = "%PDF-1.7\n".toByteArray() + ByteArray(300_000) { (it % 251).toByte() }
    private val refreshed = mutableListOf<String>()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        db = Room.inMemoryDatabaseBuilder(context, QuireDatabase::class.java).allowMainThreadQueries().build()
        paths = UploadPaths(tmp.root)
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun realApi() =
        QuireClientFactory.uploadApi(
            FakeServerConfig(server.url("/").toString()),
            FakeTokenProvider("tok"),
            SessionEvents(),
            NetworkOptions(allowCleartextLocal = true),
        )

    private fun deps(api: UploadApi = realApi()) =
        UploadDeps(
            db.uploadDao(),
            api,
            paths,
            refresh = { refreshed += it },
            clock = { 7L },
            pollDelaysMillis = listOf(0L, 0L),
        )

    private fun worker(
        deps: UploadDeps,
        attempt: Int = 0,
    ) = TestListenableWorkerBuilder<UploadWorker>(context)
        .setWorkerFactory(UploadWorkerFactory(deps))
        .setInputData(workDataOf(UploadWorker.KEY_UPLOAD_ID to "u1"))
        .setRunAttemptCount(attempt)
        .build()

    private fun staged(
        bytes: ByteArray,
        id: String = "u1",
    ) = paths.staged(id).also {
        it.parentFile!!.mkdirs()
        it.writeBytes(bytes)
    }

    private suspend fun seed(
        name: String = "Lecture 3.pdf",
        target: String = "document",
        format: String = "pdf",
        bytes: ByteArray = pdfBytes,
        withFile: Boolean = true,
    ) {
        val file = if (withFile) staged(bytes) else paths.staged("u1")
        db.uploadDao().insert(
            UploadEntity(
                "u1",
                "c1",
                target,
                format,
                name,
                file.absolutePath,
                bytes.size.toLong(),
                UploadEntity.QUEUED,
                0,
                null,
                null,
                false,
                1,
                1,
            ),
        )
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun jobJson(
        status: String,
        extra: String = "",
    ) = """{"uploadId":"srv1","status":"$status"$extra}"""

    @Test
    fun pdfHappyPath_sendsKeyTypeNameAndBody_thenPollsToDone_refreshesAndCleansUp() =
        runBlocking {
            seed()
            server.enqueue(json(fixture("createUpload.202.document.json")).setResponseCode(202))
            server.enqueue(json(fixture("getUpload.200.done.json")))

            assertEquals(Result.success(), worker(deps()).doWork())

            val post = server.takeRequest()
            assertEquals("POST", post.method)
            assertEquals("u1", post.getHeader("Idempotency-Key"))
            assertEquals("Bearer tok", post.getHeader("Authorization"))
            assertEquals("application/pdf", post.getHeader("Content-Type"))
            assertEquals("document", post.requestUrl!!.queryParameter("kind"))
            assertEquals("Lecture 3.pdf", post.requestUrl!!.queryParameter("name"))
            assertEquals("/api/mobile/v1/courses/c1/uploads", post.requestUrl!!.encodedPath)
            assertArrayEquals(pdfBytes, post.body.readByteArray())
            assertTrue(server.takeRequest().path!!.endsWith("/uploads/5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a"))
            assertEquals(listOf("c1"), refreshed)
            assertNull(db.uploadDao().get("u1"))
            assertFalse(paths.staged("u1").exists())
        }

    @Test
    fun markdownNote_sendsKindNoteAndTextMarkdown_andHebrewNameIsEncoded() =
        runBlocking {
            val md = "# שלום".toByteArray()
            seed(name = "שבוע 1.md", target = "note", format = "markdown", bytes = md)
            server.enqueue(json(fixture("createUpload.202.note.json")).setResponseCode(202))
            server.enqueue(json(fixture("getUpload.200.note.json")))

            assertEquals(Result.success(), worker(deps()).doWork())

            val post = server.takeRequest()
            assertEquals("text/markdown", post.getHeader("Content-Type"))
            assertEquals("note", post.requestUrl!!.queryParameter("kind"))
            assertEquals("שבוע 1.md", post.requestUrl!!.queryParameter("name"))
            assertArrayEquals(md, post.body.readByteArray())
            assertNull(db.uploadDao().get("u1"))
        }

    @Test
    fun serverFailedJob_marksFailedWithTheServerError_keepsTheFile_andFlagsANewKeyRetry() =
        runBlocking {
            seed()
            server.enqueue(json(jobJson("queued")).setResponseCode(202))
            server.enqueue(json(jobJson("failed", ""","error":"Could not read the PDF: bad xref"""")))

            assertEquals(Result.failure(), worker(deps()).doWork())

            val row = db.uploadDao().get("u1")!!
            assertEquals(UploadEntity.FAILED, row.state)
            assertEquals("Could not read the PDF: bad xref", row.error)
            assertTrue(row.serverFailed)
            assertTrue(paths.staged("u1").exists())
            assertTrue(refreshed.isEmpty())
        }

    @Test
    fun serverErrorThenSuccess_retriesWithTheSameIdempotencyKey() =
        runBlocking {
            seed()
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(Result.retry(), worker(deps()).doWork())
            assertEquals(UploadEntity.QUEUED, db.uploadDao().get("u1")!!.state)

            server.enqueue(json(jobJson("done", ""","kind":"document","fileId":"f1"""")).setResponseCode(202))
            assertEquals(Result.success(), worker(deps(), attempt = 1).doWork())

            assertEquals("u1", server.takeRequest().getHeader("Idempotency-Key"))
            assertEquals("u1", server.takeRequest().getHeader("Idempotency-Key"))
        }

    @Test
    fun ioExceptionMidBody_retries_andAttemptsExhaustedFails() =
        runBlocking {
            seed()
            val api =
                object : UploadApi {
                    override suspend fun create(
                        courseSlug: String,
                        idempotencyKey: String,
                        kind: String,
                        name: String,
                        body: RequestBody,
                    ): Response<UploadJobDto> = throw IOException("connection reset")

                    override suspend fun job(uploadId: String): Response<UploadJobDto> = error("unused")
                }

            assertEquals(Result.retry(), worker(deps(api)).doWork())
            assertEquals(UploadEntity.QUEUED, db.uploadDao().get("u1")!!.state)
            assertEquals(
                Result.failure(),
                worker(deps(api), attempt = UploadWorker.MAX_ATTEMPTS - 1).doWork(),
            )
            val row = db.uploadDao().get("u1")!!
            assertEquals(UploadEntity.FAILED, row.state)
            assertFalse("a network failure is retryable with the same key", row.serverFailed)
        }

    @Test
    fun permanentHttpErrors_failWithAMessage_andNeverRetry() =
        runBlocking {
            val cases =
                listOf(
                    401 to "Signed out: pair this device again",
                    404 to "Course not found on the server",
                    413 to "Upload is larger than 2 MB",
                    400 to "Markdown must be UTF-8 text",
                )
            for ((code, message) in cases) {
                db.uploadDao().deleteAll()
                seed()
                server.enqueue(
                    json("""{"error":{"code":"invalid_request","message":"$message"}}""").setResponseCode(code),
                )

                assertEquals("HTTP $code", Result.failure(), worker(deps()).doWork())

                val row = db.uploadDao().get("u1")!!
                assertEquals(UploadEntity.FAILED, row.state)
                assertEquals(message, row.error)
                assertFalse(row.serverFailed)
            }
        }

    @Test
    fun stillProcessingAfterThePollWindow_retries_andTheNextRunDoesNotPostAgain() =
        runBlocking {
            seed()
            server.enqueue(json(jobJson("queued")).setResponseCode(202))
            server.enqueue(json(jobJson("processing")))
            server.enqueue(json(jobJson("processing")))

            assertEquals(Result.retry(), worker(deps()).doWork())
            val mid = db.uploadDao().get("u1")!!
            assertEquals(UploadEntity.PROCESSING, mid.state)
            assertEquals("srv1", mid.serverUploadId)
            assertEquals(3, server.requestCount)

            server.enqueue(json(jobJson("done", ""","kind":"document","fileId":"f1"""")))
            assertEquals(Result.success(), worker(deps(), attempt = 1).doWork())

            assertEquals(4, server.requestCount)
            repeat(3) { server.takeRequest() }
            val last = server.takeRequest()
            assertEquals("GET", last.method)
            assertNull(db.uploadDao().get("u1"))
        }

    @Test
    fun missingStagedFile_failsWithAClearMessage() =
        runBlocking {
            seed(withFile = false)

            assertEquals(Result.failure(), worker(deps()).doWork())

            val row = db.uploadDao().get("u1")!!
            assertEquals(UploadEntity.FAILED, row.state)
            assertEquals("The file is no longer available; pick it again", row.error)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun aCancelledRow_makesTheWorkerFail_withoutTouchingTheServer() =
        runBlocking {
            assertEquals(Result.failure(), worker(deps()).doWork())
            assertEquals(0, server.requestCount)
        }

    @Test
    fun anAcceptedJobThatIsAlreadyDone_finishesWithoutPolling() =
        runBlocking {
            seed()
            server.enqueue(json(jobJson("done", ""","kind":"document","fileId":"f1"""")).setResponseCode(202))

            assertEquals(Result.success(), worker(deps()).doWork())

            assertEquals(1, server.requestCount)
            assertNull(db.uploadDao().get("u1"))
            assertEquals(listOf("c1"), refreshed)
        }

    @Test
    fun aRunAttemptCountAtTheCap_failsWithoutPosting_andRetryKeepsTheKey() =
        runBlocking {
            seed()

            assertEquals(Result.failure(), worker(deps(), attempt = UploadWorker.MAX_ATTEMPTS).doWork())

            val row = db.uploadDao().get("u1")!!
            assertEquals(UploadEntity.FAILED, row.state)
            assertEquals("Upload kept being interrupted; retry on a faster connection", row.error)
            assertFalse(row.serverFailed)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun aServerJobThatNeverFinishes_isFailedWithAFreshKeyRetry() =
        runBlocking {
            seed()
            val row = db.uploadDao().get("u1")!!
            db.uploadDao().update(row.copy(serverUploadId = "srv1", state = UploadEntity.PROCESSING))

            assertEquals(Result.failure(), worker(deps(), attempt = UploadWorker.MAX_POLL_RUNS).doWork())

            val after = db.uploadDao().get("u1")!!
            assertEquals(UploadEntity.FAILED, after.state)
            assertTrue(after.serverFailed)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun anUnexpectedException_failsTheRow_insteadOfStrandingIt() =
        runBlocking {
            seed()
            val api =
                object : UploadApi {
                    override suspend fun create(
                        courseSlug: String,
                        idempotencyKey: String,
                        kind: String,
                        name: String,
                        body: RequestBody,
                    ): Response<UploadJobDto> = error("boom")

                    override suspend fun job(uploadId: String): Response<UploadJobDto> = error("unused")
                }

            assertEquals(Result.failure(), worker(deps(api)).doWork())

            val row = db.uploadDao().get("u1")!!
            assertEquals(UploadEntity.FAILED, row.state)
            assertEquals("Unexpected error: boom", row.error)
        }

    @Test
    fun aStoppedUpload_showsAsWaitingAgain() =
        runBlocking {
            seed()
            val started = kotlinx.coroutines.CompletableDeferred<Unit>()
            val api =
                object : UploadApi {
                    override suspend fun create(
                        courseSlug: String,
                        idempotencyKey: String,
                        kind: String,
                        name: String,
                        body: RequestBody,
                    ): Response<UploadJobDto> {
                        started.complete(Unit)
                        kotlinx.coroutines.awaitCancellation()
                    }

                    override suspend fun job(uploadId: String): Response<UploadJobDto> = error("unused")
                }
            val running =
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    worker(deps(api)).doWork()
                }
            started.await()
            assertEquals(UploadEntity.UPLOADING, db.uploadDao().get("u1")!!.state)

            running.cancelAndJoin()

            assertEquals(UploadEntity.QUEUED, db.uploadDao().get("u1")!!.state)
        }
}
