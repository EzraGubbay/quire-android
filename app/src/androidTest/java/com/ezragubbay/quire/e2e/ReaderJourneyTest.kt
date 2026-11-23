package com.ezragubbay.quire.e2e

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.ezragubbay.quire.MainActivity
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.download.DownloadWorkerFactory
import com.ezragubbay.quire.testing.MockWebServerRule
import com.ezragubbay.quire.testing.SessionSeedRule
import com.ezragubbay.quire.testing.Smoke
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Epic e2e journey for QMOB-E5 (reader: downloads, PDF viewer, open-file, downloads library, share,
 * notes reader). Defined before any E5 story is implemented (QMOB-E5-S0, #35): every case ships
 * Ignored with reason QMOB-E5 and is un-ignored by the story (#36–#42) that satisfies it. The epic
 * is done when no such Ignore remains here (a release PR into `main` fails while any remains).
 *
 * (This KDoc deliberately avoids the literal annotation text so the release-gate grep over
 * `app/src/androidTest` matches only the real annotations, not this note — the false-match lesson
 * from E2's PR #78/#79 and carried through E3/E4.)
 *
 * The journey finds UI by **test tag only** (the contract in `docs/e2e/E5-reader.md`), never by
 * display text — RTL-safe for Hebrew titles and formulae — and runs against [MockWebServerRule]
 * fixtures whose shapes mirror the real `mobile-v1` reader contract: the file-content endpoint
 * `GET /api/mobile/v1/courses/{course}/files/{fileId}/content?format=pdf|original|markdown` with HTTP
 * `Range`/`ETag` (a small 5-page PDF byte fixture, [DECK_PDF]), and the note endpoint
 * `GET /courses/{course}/notes/{noteId}` returning Markdown. The seeded session ([SessionSeedRule]
 * `signedIn = true`) points the app at [MockWebServerRule.baseUrl], so the whole journey is hermetic.
 *
 * Each case awaits a **content** node (a rendered page, a populated row, the markdown body) — never a
 * bare screen-root tag that is also present during Loading — so later impl stories' cases do not
 * flake at the CI gate (the E4 journey-sync lesson).
 *
 * Journey: open a file from a course → stream-to-cache → PDF viewer (pages render, page indicator,
 * scroll, zoom, resume on reopen) → toggle download (persist offline) → the file still opens with the
 * server down → Downloads tab lists it with its size and total storage use → remove / retry →
 * share / save a copy → open a note in the notes reader (Markdown; math if the spike lands).
 *
 * Case → owning E5 story (the story that removes the Ignore):
 *  - #36 (S1, download engine): underpins every download/offline case (WorkManager + Room state) but
 *    un-ignores none directly; verified by its own unit tests (`DownloadWorkerTest`, …).
 *  - #37 (S2, PDF viewer): first-page render + page indicator, scroll, zoom, resume-on-reopen.
 *  - #38 (S3, open-file flow): stream-to-cache on tap, download toggle → done, offline open,
 *    original-only → open-with.
 *  - #39 (S4, Downloads tab): offline library row + size + storage use, remove, retry.
 *  - #40 (S5, share / save): Share and Save-a-copy actions (last non-notes Ignore removed).
 *  - #42 (S6, notes reader): Markdown body; block math per ADR 0001 (#41 spike).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ReaderJourneyTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    // The hermetic server starts first so the seeded session can point at it (files/notes stream from it).
    @get:Rule(order = 1)
    val server = MockWebServerRule(answerSessionCheckOutOfBand = true)

    // The reader surface is only reachable from a paired (signed-in) install.
    @get:Rule(order = 2)
    val session = SessionSeedRule(signedIn = true, serverUrl = { server.baseUrl })

    @get:Rule(order = 3)
    val composeRule = createEmptyComposeRule()

    @Inject lateinit var db: QuireDatabase

    @Inject lateinit var workerFactory: DownloadWorkerFactory

    private var scenario: ActivityScenario<MainActivity>? = null

    /** Each case starts with no persisted downloads and an empty viewer cache (state survives between cases). */
    @Before
    fun resetOfflineState() {
        hilt.inject()
        // HiltTestApplication is not a WorkManager Configuration.Provider, so wire the download worker factory here.
        val context = ApplicationProvider.getApplicationContext<Context>()
        // WorkManager initialises once per process, but each case has a fresh Hilt graph (and Room instance), so the
        // factory it holds delegates to the current case's factory.
        currentWorkerFactory = workerFactory
        runCatching {
            WorkManager.initialize(context, Configuration.Builder().setWorkerFactory(DelegatingWorkerFactory).build())
        } // already initialised by an earlier case in this process
        WorkManager
            .getInstance(context)
            .cancelAllWork()
            .result
            .get() // no download work leaks between cases
        // Direct DAO/file reset: going through DownloadRepository would touch WorkManager before the app starts.
        runBlocking { db.downloadDao().deleteAll() }
        context.cacheDir.resolve("viewer").deleteRecursively()
        context.filesDir.resolve("downloads").deleteRecursively()
    }

    @After
    fun closeScenario() {
        scenario?.close()
    }

    // --- PDF viewer & resume: un-ignored by QMOB-E5-S2 (#37) ------------------------------------

    @Test
    @Smoke("open-pdf")
    fun openDeck_rendersFirstPage_withPageIndicator() {
        enqueueReaderBundle()
        launch()
        openLectureFile()

        // The viewer streams the deck to cache and renders page 1; the indicator reads 'n / N'.
        awaitTag("pdf-page-1") // wait on a rendered page, not the (also-loading) viewer root
        composeRule.onNodeWithTag("screen-pdf-viewer").assertIsDisplayed()
        composeRule.onNodeWithTag("pdf-page-1").assertIsDisplayed()
        composeRule.onNodeWithTag("page-indicator").assertIsDisplayed()
    }

    @Test
    fun pdfViewer_scrollToThirdPage_rendersThatPage() {
        enqueueReaderBundle()
        launch()
        openLectureFile()
        awaitTag("pdf-page-1")

        // Lazy, off-main-thread paging: scrolling the viewer renders page 3 at viewport resolution.
        composeRule
            .onNodeWithTag("screen-pdf-viewer")
            .performScrollToNode(hasTestTag("pdf-page-3"))
        composeRule.onNodeWithTag("pdf-page-3").assertIsDisplayed()
    }

    @Test
    fun pdfViewer_doubleTap_zoomsPage() {
        enqueueReaderBundle()
        launch()
        openLectureFile()
        awaitTag("pdf-page-1")

        // Double-tap zooms and re-renders the page crisply; the page stays on screen.
        composeRule.onNodeWithTag("pdf-page-1").performTouchInput { doubleClick() }
        composeRule.onNodeWithTag("pdf-page-1").assertIsDisplayed()
    }

    @Test
    fun pdfViewer_reopen_resumesLastReadPage() {
        enqueueReaderBundle()
        launch()
        openLectureFile()
        awaitTag("pdf-page-1")

        // Read to page 3, then leave the viewer...
        composeRule
            .onNodeWithTag("screen-pdf-viewer")
            .performScrollToNode(hasTestTag("pdf-page-3"))
        composeRule.onNodeWithTag("pdf-page-3").assertIsDisplayed()
        pressBack()

        // ...reopen the same file: the last-read page (persisted in Room) is restored.
        enqueuePdf() // reopening may re-stream to the cache
        reopenLectureFile()
        awaitTag("pdf-page-3")
        composeRule.onNodeWithTag("pdf-page-3").assertIsDisplayed()
    }

    // --- Open-file flow (stream-to-cache, download toggle, open-with): QMOB-E5-S3 (#38) ---------

    // un-ignored by QMOB-E5-S3 (#38)
    @Test
    fun tapFile_streamsContentToCache_thenShowsViewer() {
        enqueueReaderBundle()
        launch()
        openLectureFile()

        // The tap fetches the PDF format of the content endpoint into the viewer cache, then renders.
        awaitTag("pdf-page-1")
        val content = takeContentRequest()
        assertTrue("Open must GET the file-content endpoint", content.path!!.contains("/content"))
        assertTrue("Open must request the PDF format", content.path!!.contains("format=pdf"))
        composeRule.onNodeWithTag("screen-pdf-viewer").assertIsDisplayed()
    }

    // un-ignored by QMOB-E5-S3 (#38)
    @Test
    fun viewer_toggleDownload_persistsOffline_reachesDoneState() {
        enqueueReaderBundle()
        launch()
        openLectureFile()
        awaitTag("pdf-page-1")

        // Idle at first; one tap on the viewer toggle enqueues the download, which reaches DONE.
        composeRule.onNodeWithTag("download-state-idle").assertIsDisplayed()
        composeRule.onNodeWithTag("viewer-download-toggle").performClick()
        awaitTag("download-state-done")
        composeRule.onNodeWithTag("download-state-done").assertIsDisplayed()
    }

    // un-ignored by QMOB-E5-S3 (#38)
    @Test
    fun downloadedFile_opensWithServerDown() {
        enqueueReaderBundle()
        launch()
        openLectureFile()
        awaitTag("pdf-page-1")
        composeRule.onNodeWithTag("viewer-download-toggle").performClick()
        awaitTag("download-state-done")
        pressBack()

        // With the file downloaded the server can vanish; reopening still renders from disk.
        server.server.shutdown()
        reopenLectureFile()
        awaitTag("pdf-page-1")
        composeRule.onNodeWithTag("pdf-page-1").assertIsDisplayed()
    }

    // un-ignored by QMOB-E5-S3 (#38)
    @Test
    fun originalOnlyFile_offersOpenWith() {
        enqueueCourses()
        enqueueCourseDetailWithFiles("reader-files-original.json")
        launch()
        openFilesTab()

        // A file with only an original (no PDF) cannot render in-app; it offers an 'Open with…' route.
        composeRule.onNodeWithTag("file-row-$ORIGINAL_ONLY_FILE_ID").performClick()
        awaitTag("action-open-with")
        composeRule.onNodeWithTag("action-open-with").assertIsDisplayed()
    }

    // --- Downloads tab (offline library, storage, remove / retry): QMOB-E5-S4 (#39) -------------

    // QMOB-E5-S4 (#39)
    @Test
    fun downloadsTab_listsOfflineFile_withSizeAndStorageUse() {
        enqueueReaderBundle()
        launch()
        openLectureFile()
        awaitTag("pdf-page-1")
        composeRule.onNodeWithTag("viewer-download-toggle").performClick()
        awaitTag("download-state-done")
        pressBack()

        // The Downloads tab lists the downloaded file (with size) and shows total storage used.
        pressBack() // course screen -> courses list, where the bottom bar lives
        composeRule.onNodeWithTag("nav-downloads").performClick()
        composeRule.onNodeWithTag("screen-downloads").assertIsDisplayed()
        awaitTag("download-row-$LECTURE_FILE_ID")
        composeRule.onNodeWithTag("downloads-list").assertIsDisplayed()
        composeRule.onNodeWithTag("download-row-$LECTURE_FILE_ID").assertIsDisplayed()
        composeRule.onNodeWithTag("storage-usage").assertIsDisplayed()
    }

    // QMOB-E5-S4 (#39)
    @Test
    fun downloadsTab_removeFile_removesRow() {
        enqueueReaderBundle()
        launch()
        openLectureFile()
        awaitTag("pdf-page-1")
        composeRule.onNodeWithTag("viewer-download-toggle").performClick()
        awaitTag("download-state-done")
        pressBack()
        pressBack() // course screen -> courses list, where the bottom bar lives
        composeRule.onNodeWithTag("nav-downloads").performClick()
        awaitTag("download-row-$LECTURE_FILE_ID")

        // Removing the download deletes the file and drops it from the list.
        composeRule.onNodeWithTag("download-remove").performClick()
        composeRule.onNodeWithTag("download-row-$LECTURE_FILE_ID").assertDoesNotExist()
    }

    // QMOB-E5-S4 (#39)
    @Test
    fun downloadsTab_retryFailedDownload_restarts() {
        enqueueCourses()
        enqueueCourseDetail()
        enqueuePdf() // viewing streams to cache (not a download)
        server.enqueueFixture("error-not-found.json", code = 404) // the persisted download fails
        launch()
        openLectureFile()
        awaitTag("pdf-page-1")
        composeRule.onNodeWithTag("viewer-download-toggle").performClick()
        awaitTag("download-state-failed")
        enqueueCourses() // returning to the courses list refreshes it; keep that from eating the retry's PDF
        pressBack()

        // A failed download surfaces a failed state with a retry; retrying re-enqueues the work.
        pressBack() // course screen -> courses list, where the bottom bar lives
        composeRule.onNodeWithTag("nav-downloads").performClick()
        awaitTag("download-row-$LECTURE_FILE_ID")
        composeRule.onNodeWithTag("download-state-failed").assertIsDisplayed()
        enqueuePdf() // the retry succeeds
        composeRule.onNodeWithTag("download-retry").performClick()
        awaitTag("download-state-done")
        composeRule.onNodeWithTag("download-state-done").assertIsDisplayed()
    }

    // --- Share / save a copy: un-ignored by QMOB-E5-S5 (#40) ------------------------------------

    // un-ignored by QMOB-E5-S5 (#40)
    @Test
    fun viewer_offersShareAndSaveCopy_forDownloadedFile() {
        enqueueReaderBundle()
        launch()
        openLectureFile()
        awaitTag("pdf-page-1")
        composeRule.onNodeWithTag("viewer-download-toggle").performClick()
        awaitTag("download-state-done")

        // A downloaded file can be shared (ACTION_SEND) or saved to the device's Downloads folder.
        composeRule.onNodeWithTag("action-share").assertIsDisplayed()
        composeRule.onNodeWithTag("action-save-copy").assertIsDisplayed()
    }

    // --- Notes reader (Markdown + math): un-ignored by QMOB-E5-S6 (#42) --------------------------

    @Test
    fun openNote_fromNotesTab_rendersMarkdownBody() {
        enqueueCourses()
        enqueueCourseDetail()
        server.enqueueFixture("reader-note.json") // GET /courses/{course}/notes/{noteId}
        launch()
        openSignalsCourse()
        composeRule.onNodeWithTag("tab-notes").performClick()
        composeRule.onNodeWithTag("note-row-$LECTURE_NOTE_ID").performClick()

        // The read-only reader renders the Markdown body (headings, paragraphs, lists).
        awaitTag("note-markdown")
        composeRule.onNodeWithTag("screen-note-reader").assertIsDisplayed()
        composeRule.onNodeWithTag("note-markdown").assertIsDisplayed()
    }

    @Test
    fun noteReader_rendersMathBlock() {
        enqueueCourses()
        enqueueCourseDetail()
        server.enqueueFixture("reader-note.json")
        launch()
        openSignalsCourse()
        composeRule.onNodeWithTag("tab-notes").performClick()
        composeRule.onNodeWithTag("note-row-$LECTURE_NOTE_ID").performClick()
        awaitTag("note-markdown")

        // Block LaTeX ($$…$$) renders as a math node per ADR 0001 (the spike, #41).
        composeRule.onNodeWithTag("note-math").assertIsDisplayed()
    }

    // --- Helpers --------------------------------------------------------------------------------

    /** Enqueues the launch course list. */
    private fun enqueueCourses() {
        server.enqueueFixture("courses-list.json")
    }

    /**
     * Enqueues a course's detail bundle in the order the course screen fetches it:
     * `GET /courses/{course}` then `.../sessions`, `.../files`, `.../notes`.
     */
    private fun enqueueCourseDetail() {
        server.enqueueFixture("course-detail.json")
        server.enqueueFixture("course-sessions.json")
        server.enqueueFixture("course-files.json")
        server.enqueueFixture("course-notes.json")
    }

    /** Like [enqueueCourseDetail] but with a specific files fixture (e.g. an original-only file). */
    private fun enqueueCourseDetailWithFiles(filesFixture: String) {
        server.enqueueFixture("course-detail.json")
        server.enqueueFixture("course-sessions.json")
        server.enqueueFixture(filesFixture)
        server.enqueueFixture("course-notes.json")
    }

    /** Enqueues the 5-page PDF deck as a full `200` content body (Range/ETag per the contract). */
    private fun enqueuePdf() {
        server.enqueuePdfFixture(DECK_PDF)
    }

    /** The launch + course-detail bundle + the deck content, for the common open-a-deck flow. */
    private fun enqueueReaderBundle() {
        enqueueCourses()
        enqueueCourseDetail()
        enqueuePdf() // the stream-to-cache view
        enqueuePdf() // the persisted download, when a case toggles it on
    }

    /** The next recorded request to the file-content endpoint, skipping browse/session calls. */
    private fun takeContentRequest(): RecordedRequest {
        while (true) {
            val request = server.takeRequest()
            if (request.path!!.contains("/content")) return request
        }
    }

    /** Launches [MainActivity] (signed-in: lands on the Courses shell). */
    private fun launch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        composeRule.waitForIdle()
    }

    /** Opens the Signals and Systems course from the list and waits for its screen. */
    private fun openSignalsCourse() {
        composeRule.onNodeWithTag("course-card-signals-and-systems").performClick()
        awaitTag("screen-course")
    }

    /** Opens the course and switches to the Files tab. */
    private fun openFilesTab() {
        openSignalsCourse()
        composeRule.onNodeWithTag("tab-files").performClick()
        awaitTag("file-list")
        // The file rows appear once the files response is stored, one step before the bundle's last fetch (notes)
        // goes out. Let it go first, so a request the caller triggers next cannot be handed the notes response.
        server.awaitRequest("/notes")
    }

    /** Opens the course's Files tab and taps the lecture deck row to drive the open-file flow. */
    private fun openLectureFile() {
        openFilesTab()
        composeRule.onNodeWithTag("file-row-$LECTURE_FILE_ID").performClick()
    }

    /** After backing out of the viewer we are still on the course screen: re-select Files and tap the row. */
    private fun reopenLectureFile() {
        composeRule.onNodeWithTag("tab-files").performClick()
        awaitTag("file-list")
        composeRule.onNodeWithTag("file-row-$LECTURE_FILE_ID").performClick()
    }

    /** Dispatches a system back press (on the activity's main thread), then lets recomposition settle. */
    private fun pressBack() {
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    /** Waits until at least one node with [tag] is present (async Room/network/render settle). */
    private fun awaitTag(tag: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private object DelegatingWorkerFactory : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? = currentWorkerFactory?.createWorker(appContext, workerClassName, workerParameters)
    }

    private companion object {
        @Volatile var currentWorkerFactory: DownloadWorkerFactory? = null

        const val TIMEOUT_MS = 10_000L
        const val DECK_PDF = "lecture-1.pdf"
        const val LECTURE_FILE_ID = "6f1c2a52-8b0e-4c6a-9d63-2a3a7c1d9e10"
        const val LECTURE_NOTE_ID = "0d6f6c3e-4f5a-4b0b-8f0e-9a6b1d2c3e4f"
        const val ORIGINAL_ONLY_FILE_ID = "8b3e4c74-ac2e-4e8c-bf85-4c5c9e3fba32"
    }
}
