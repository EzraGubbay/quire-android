package com.ezragubbay.quire.e2e

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.ezragubbay.quire.MainActivity
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.download.DownloadWorkerFactory
import com.ezragubbay.quire.core.upload.UploadWorkerFactory
import com.ezragubbay.quire.core.upload.WorkManagerUploadScheduler
import com.ezragubbay.quire.sync.SyncWorkerFactory
import com.ezragubbay.quire.testing.MockWebServerRule
import com.ezragubbay.quire.testing.SessionSeedRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * E7 upload journeys (QMOB-E7-S3, #168): pick a PDF or Markdown file from a course's Files or Notes tab and watch it
 * go through the upload queue. These cases are live: none is ignored.
 *
 * The system document picker is replaced with Espresso-Intents (`intending` answers the open-document request with a
 * `file:` URI to a fixture the test writes into the app cache), the server is the hermetic [MockWebServerRule], and
 * background work is driven by the WorkManager test driver (the upload worker only runs once the test meets its
 * network constraint). UI is found by test tag only; the pending row's tag carries the queue id, which the test reads
 * from Room. Contract: `docs/e2e/E7-sync.md`.
 *
 * Cases:
 *  1. Files tab, a PDF: the row appears, the worker POSTs and polls to done, the row disappears and the refetched
 *     course shows the new file.
 *  2. Notes tab, a Markdown file: the new note appears in the list.
 *  3. Notes tab, a PDF: uploaded as a document (`kind=document`), never refused.
 *  4. The server reports the job failed: the row shows its failed state with a Retry action.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UploadJourneyTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val server = MockWebServerRule(answerSessionCheckOutOfBand = true)

    @get:Rule(order = 2)
    val session = SessionSeedRule(signedIn = true, serverUrl = { server.baseUrl })

    @get:Rule(order = 3)
    val composeRule = createEmptyComposeRule()

    @Inject lateinit var db: QuireDatabase

    @Inject lateinit var uploadWorkerFactory: UploadWorkerFactory

    @Inject lateinit var downloadWorkerFactory: DownloadWorkerFactory

    @Inject lateinit var syncWorkerFactory: SyncWorkerFactory

    private var scenario: ActivityScenario<MainActivity>? = null
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hilt.inject()
        currentWorkerFactories = listOf(uploadWorkerFactory, downloadWorkerFactory, syncWorkerFactory)
        runCatching {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration
                    .Builder()
                    .setExecutor(SynchronousExecutor())
                    .setWorkerFactory(DelegatingWorkerFactory)
                    .build(),
            )
        }
        WorkManager
            .getInstance(context)
            .cancelAllWork()
            .result
            .get()
        runBlocking { db.uploadDao().deleteAll() }
        context.filesDir.resolve("uploads").deleteRecursively()
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
        scenario?.close()
    }

    @Test
    fun pdfFromFilesTab_uploads_andTheNewFileAppears() {
        openCourse()
        openTab("tab-files", "file-list")
        stubPicker(fixtureFile("lecture-1.pdf"))

        composeRule.onNodeWithTag("action-upload-file").performClick()
        val id = awaitQueuedUpload()
        composeRule.onNodeWithTag("upload-row-$id").assertIsDisplayed()

        enqueueUploadToDone("upload-done-document.json", files = "course-files-uploaded.json")
        meetUploadConstraints(id)

        awaitGone("upload-row-$id")
        awaitTag("file-row-$NEW_FILE_ID")
        composeRule.onNodeWithTag("file-row-$NEW_FILE_ID").assertIsDisplayed()
        val post = server.awaitRequest("name=lecture-1.pdf")
        assertEquals("POST", post.method)
        assertEquals(id, post.getHeader("Idempotency-Key"))
        assertEquals("application/pdf", post.getHeader("Content-Type"))
        assertEquals("document", post.requestUrl!!.queryParameter("kind"))
    }

    @Test
    fun markdownFromNotesTab_uploads_andTheNewNoteAppears() {
        openCourse()
        openTab("tab-notes", "note-list")
        stubPicker(fixtureFile("week1.md"))

        composeRule.onNodeWithTag("action-upload-note").performClick()
        val id = awaitQueuedUpload()

        enqueueUploadToDone("upload-done-note.json", notes = "course-notes-uploaded.json")
        meetUploadConstraints(id)

        awaitGone("upload-row-$id")
        awaitTag("note-row-$NEW_NOTE_ID")
        composeRule.onNodeWithTag("note-row-$NEW_NOTE_ID").assertIsDisplayed()
        val post = server.awaitRequest("name=week1.md")
        assertEquals("text/markdown", post.getHeader("Content-Type"))
        assertEquals("note", post.requestUrl!!.queryParameter("kind"))
    }

    @Test
    fun pdfFromNotesTab_isUploadedAsADocument() {
        openCourse()
        openTab("tab-notes", "note-list")
        stubPicker(fixtureFile("lecture-1.pdf"))

        composeRule.onNodeWithTag("action-upload-note").performClick()
        // A document upload shows its row on the Files tab, so the Notes tab has no row to wait for.
        val id = awaitQueuedUpload(rowVisible = false)

        enqueueUploadToDone("upload-done-document.json", files = "course-files-uploaded.json")
        meetUploadConstraints(id)

        composeRule.waitUntil(TIMEOUT_MS) { runBlocking { db.uploadDao().get(id) } == null }
        val post = server.awaitRequest("name=lecture-1.pdf")
        assertEquals("document", post.requestUrl!!.queryParameter("kind"))
        assertEquals("application/pdf", post.getHeader("Content-Type"))
    }

    @Test
    fun serverFailure_showsTheFailedState_withRetry() {
        openCourse()
        openTab("tab-files", "file-list")
        stubPicker(fixtureFile("lecture-1.pdf"))

        composeRule.onNodeWithTag("action-upload-file").performClick()
        val id = awaitQueuedUpload()

        server.enqueueFixture("upload-accepted.json", code = 202)
        server.enqueueFixture("upload-failed.json")
        meetUploadConstraints(id)

        awaitTag("upload-state-failed")
        composeRule.onNodeWithTag("upload-state-failed").assertIsDisplayed()
        composeRule.onNodeWithTag("action-retry-upload").assertIsDisplayed()
        composeRule.onNodeWithTag("action-dismiss-upload").assertIsDisplayed()
    }

    // --- Helpers --------------------------------------------------------------------------------

    private fun openCourse() {
        server.enqueueFixture("courses-list.json")
        server.enqueueFixture("course-detail.json")
        server.enqueueFixture("course-sessions.json")
        server.enqueueFixture("course-files.json")
        server.enqueueFixture("course-notes.json")
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("course-card-signals-and-systems").performClick()
        awaitTag("screen-course")
    }

    /** Switches to a tab, waits for its list, then for the course bundle's last (notes) request. */
    private fun openTab(
        tabTag: String,
        listTag: String,
    ) {
        composeRule.onNodeWithTag(tabTag).performClick()
        awaitTag(listTag)
        server.awaitRequest("/notes")
    }

    /** The picker answers with [file]; nothing from the real system picker is shown. */
    private fun stubPicker(file: File) {
        val data = Intent().setData(Uri.fromFile(file))
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, data))
    }

    /** Copies a fixture asset into the app cache, so the picked `file:` URI is readable by the app. */
    private fun fixtureFile(name: String): File {
        val target = File(context.cacheDir, name)
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open("fixtures/$name")
            .use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    /** Waits for the staged upload to be queued in Room and returns its id (the row's test-tag suffix). */
    private fun awaitQueuedUpload(rowVisible: Boolean = true): String {
        composeRule.waitUntil(TIMEOUT_MS) { runBlocking { db.uploadDao().getAll() }.isNotEmpty() }
        val id = runBlocking { db.uploadDao().getAll() }.single().id
        if (rowVisible) awaitTag("upload-row-$id")
        return id
    }

    /**
     * Queues the responses the worker consumes, in order: the accepted job, the done job, then the course refetch
     * (`refreshCourse`: detail, sessions, files, notes) carrying the new file or note.
     */
    private fun enqueueUploadToDone(
        done: String,
        files: String = "course-files.json",
        notes: String = "course-notes.json",
    ) {
        server.enqueueFixture("upload-accepted.json", code = 202)
        server.enqueueFixture(done)
        server.enqueueFixture("course-detail.json")
        server.enqueueFixture("course-sessions.json")
        server.enqueueFixture(files)
        server.enqueueFixture(notes)
    }

    /** Meets the network constraint of the upload's work, so the worker runs under the test driver. */
    private fun meetUploadConstraints(id: String) {
        val info =
            checkNotNull(
                WorkManager
                    .getInstance(context)
                    .getWorkInfosForUniqueWork(WorkManagerUploadScheduler.uniqueName(id))
                    .get()
                    .firstOrNull(),
            ) { "No upload work was enqueued for $id" }
        requireNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setAllConstraintsMet(info.id)
        composeRule.waitForIdle()
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitGone(tag: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    /** WorkManager instantiates workers through this; the first current-case factory that knows the class wins. */
    private object DelegatingWorkerFactory : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? =
            currentWorkerFactories.firstNotNullOfOrNull {
                it.createWorker(appContext, workerClassName, workerParameters)
            }
    }

    private companion object {
        @Volatile var currentWorkerFactories: List<WorkerFactory> = emptyList()

        const val TIMEOUT_MS = 10_000L

        /** The file and note the mock server adds (in `course-files-uploaded.json` / `course-notes-uploaded.json`). */
        const val NEW_FILE_ID = "c3d4e5f6-7081-4b92-8c3d-4e5f60718293"
        const val NEW_NOTE_ID = "d4e5f607-8192-4ca3-9d4e-5f6071829304"
    }
}
