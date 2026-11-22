package com.ezragubbay.quire.e2e

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.ezragubbay.quire.MainActivity
import com.ezragubbay.quire.core.database.DownloadEntity
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.download.DownloadWorkerFactory
import com.ezragubbay.quire.core.download.WorkManagerDownloadScheduler
import com.ezragubbay.quire.sync.AutoDownloadPolicy
import com.ezragubbay.quire.sync.OfflineSettingsStore
import com.ezragubbay.quire.sync.SyncWorkerFactory
import com.ezragubbay.quire.testing.MockWebServerRule
import com.ezragubbay.quire.testing.SessionSeedRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Epic e2e journey for QMOB-E7 (background sync / upload). Defined before any E7 story is implemented
 * (QMOB-E7-S0, #50): every case ships Ignored with reason QMOB-E7 and is un-ignored by the story that
 * satisfies it (#51 background metadata sync, #52 offline pinning). The epic is done when no such
 * Ignore remains here (a release PR into `main` fails while any remains).
 *
 * (This KDoc deliberately avoids the literal annotation text so the release-gate grep over
 * `app/src/androidTest` matches only the real annotations, not this note — the false-match lesson
 * carried from E2's PR #78/#79 through E3/E4/E5.)
 *
 * The journey finds UI by **test tag only** (the contract in `docs/e2e/E7-sync.md`), never by display
 * text — RTL-safe for Hebrew course names — and runs against [MockWebServerRule] fixtures whose shapes
 * mirror the real `mobile-v1` browse/content contract. The seeded session ([SessionSeedRule]
 * `signedIn = true`) points the app at [MockWebServerRule.baseUrl], so the whole journey is hermetic.
 *
 * Unlike the earlier journeys, background work is driven by the **WorkManager test driver**: this class
 * installs a test WorkManager in [installTestWorkManager], so a periodic worker only runs when the test
 * releases its period delay ([runPeriodicSync] → `setPeriodDelayMet`) and a constrained one-time
 * download only runs when the test meets its constraints ([meetDownloadConstraints] →
 * `setAllConstraintsMet`). Nothing fires on a real interval or network event, so the cases are
 * deterministic.
 *
 * Two journeys (AC on #50):
 *  1. **Periodic sync surfaces a new server file.** With a course open on its Files tab, running the
 *     periodic metadata sync worker makes a file newly added on the (mock) server appear in the list
 *     with **no** manual pull-to-refresh (the Files tab observes Room; the worker upserts into it).
 *  2. **A pinned course auto-downloads the synced file and opens it offline.** With the course pinned
 *     'Available offline', the newly-synced file is downloaded automatically; with the server then shut
 *     down, tapping it still renders page 1 from disk.
 *
 * Upload journeys (QMOB-E7-S3..S5, Share-to-Quire + upload queue + status) are **pending**: the mobile
 * upload contract (QMOB-E1-S8) lands on the backend first. They are specified in `docs/e2e/E7-sync.md`
 * and will arrive here as their own cases Ignored with reason `QMOB-E7-upload`, not added by this story.
 *
 * Case → owning E7 story (the story that removes the Ignore):
 *  - #51 (S1, background metadata sync): the sync-settings controls and the periodic-sync journey.
 *  - #52 (S2, offline pinning): the pin badge, the pinned auto-download-offline journey, the
 *    storage-cap warning.
 *
 * [DelegatingWorkerFactory] chains the E5 [DownloadWorkerFactory] and the E7 [SyncWorkerFactory]; see
 * [installTestWorkManager].
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SyncJourneyTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    // The hermetic server starts first so the seeded session can point at it (sync/content stream from it).
    @get:Rule(order = 1)
    val server = MockWebServerRule(answerSessionCheckOutOfBand = true)

    // The sync/pin surfaces are only reachable from a paired (signed-in) install.
    @get:Rule(order = 2)
    val session = SessionSeedRule(signedIn = true, serverUrl = { server.baseUrl })

    @get:Rule(order = 3)
    val composeRule = createEmptyComposeRule()

    @Inject lateinit var db: QuireDatabase

    @Inject lateinit var downloadWorkerFactory: DownloadWorkerFactory

    @Inject lateinit var syncWorkerFactory: SyncWorkerFactory

    @Inject lateinit var offlineSettings: OfflineSettingsStore

    private var scenario: ActivityScenario<MainActivity>? = null

    /** Each case starts with a test WorkManager, no scheduled work, and no persisted downloads. */
    @Before
    fun installTestWorkManager() {
        hilt.inject()
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Each case has a fresh Hilt graph (and Room instance); the delegating factory reads the current one.
        currentWorkerFactories = listOf(downloadWorkerFactory, syncWorkerFactory)
        // Test WorkManager: scheduled work waits for the driver instead of a real interval / network event.
        // WorkManager initialises once per process; later cases reuse it (the factory delegates to the current case's).
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
            .get() // no sync/download work leaks between cases
        // Direct DAO/file reset: going through the repositories would touch WorkManager before the app starts.
        runBlocking {
            db.downloadDao().deleteAll()
            // Pin state lives in the shared settings DataStore, which outlives a case: start each one unpinned.
            offlineSettings.current().pinned.forEach { offlineSettings.removePin(it) }
            offlineSettings.setCapBytes(AutoDownloadPolicy.DEFAULT_CAP_BYTES)
        }
        context.cacheDir.resolve("viewer").deleteRecursively()
        context.filesDir.resolve("downloads").deleteRecursively()
    }

    @After
    fun closeScenario() {
        scenario?.close()
    }

    // --- Sync settings controls: satisfied by QMOB-E7-S1 (#51) ---------------------------------

    @Test
    fun syncSettings_exposesSyncControls() {
        enqueueCourses()
        launch()

        // The Settings screen (E2) grows a Sync section: an on/off switch, an interval, a Wi-Fi-only
        // constraint, the last-success stamp and a manual 'Sync now'.
        composeRule.onNodeWithTag("action-settings").performClick()
        awaitTag("screen-settings")
        // The Sync section sits below Appearance, so scroll each control into view first.
        for (tag in SYNC_CONTROL_TAGS) {
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun syncNow_fromSettings_stampsLastSuccess() {
        enqueueCourses()
        launch()
        composeRule.onNodeWithTag("action-settings").performClick()
        awaitTag("action-sync-now")

        // 'Sync now' forces an immediate metadata refetch; a successful one stamps the last-success time.
        enqueueCourses() // the forced refetch
        composeRule.onNodeWithTag("action-sync-now").performScrollTo().performClick()
        awaitTag("sync-last-success")
        composeRule.onNodeWithTag("sync-last-success").performScrollTo().assertIsDisplayed()
    }

    // --- Periodic metadata sync: satisfied by QMOB-E7-S1 (#51) ----------------------------------

    @Test
    fun periodicSync_surfacesNewServerFile_withoutManualRefresh() {
        enqueueCourses()
        enqueueCourseDetail() // the initial open: three files, no 'Lecture 2' yet
        launch()
        openSignalsCourse()
        selectFilesTab()

        // Before any sync, the newly-added server file is absent.
        composeRule.onNodeWithTag("file-row-$NEW_FILE_ID").assertDoesNotExist()

        // The server now has an extra file; the periodic sync worker refetches it into Room.
        enqueueSyncBundle()
        runPeriodicSync()

        // No pull-to-refresh: the Files tab, observing Room, shows the new row on its own.
        awaitTag("file-row-$NEW_FILE_ID")
        composeRule.onNodeWithTag("file-row-$NEW_FILE_ID").assertIsDisplayed()
    }

    // --- Offline pinning: un-ignored by QMOB-E7-S2 (#52) -----------------------------------------

    @Test
    fun pinCourse_marksAvailableOffline_showsPinnedBadge() {
        enqueueCourses()
        enqueueCourseDetail()
        launch()
        openSignalsCourse()

        // 'Available offline' pins the course; a badge marks it as pinned.
        awaitTag("action-pin-course")
        composeRule.onNodeWithTag("action-pin-course").performClick()
        awaitTag("pinned-badge")
        composeRule.onNodeWithTag("pinned-badge").assertIsDisplayed()
    }

    @Test
    fun pinnedCourse_autoDownloadsSyncedFile_opensOffline() {
        enqueueCourses()
        enqueueCourseDetail()
        launch()
        openSignalsCourse()

        // Pin the course 'Available offline'.
        awaitTag("action-pin-course")
        composeRule.onNodeWithTag("action-pin-course").performClick()
        awaitTag("pinned-badge")
        selectFilesTab()

        // A sync brings in a new file; a pinned course auto-enqueues its download.
        enqueueSyncBundle()
        enqueuePdf() // the auto-download streams the new file's PDF
        runPeriodicSync()
        awaitTag("file-row-$NEW_FILE_ID")
        meetDownloadConstraints(NEW_FILE_ID) // release the constrained one-time download
        awaitDownloaded(NEW_FILE_ID) // finish streaming before the server vanishes

        // With the new file cached offline, the server can vanish and it still opens from disk.
        server.server.shutdown()
        composeRule.onNodeWithTag("file-row-$NEW_FILE_ID").performClick()
        awaitTag("pdf-page-1")
        composeRule.onNodeWithTag("pdf-page-1").assertIsDisplayed()
    }

    @Test
    fun pinningOverStorageCap_showsWarning() {
        enqueueCourses()
        enqueueCourseDetail()
        launch()
        openSignalsCourse()

        // Pinning a course whose offline footprint would exceed the storage cap surfaces a warning: the pin is
        // kept, auto-download pauses, and the warning shows. A one-byte cap drives the over-cap condition.
        runBlocking { offlineSettings.setCapBytes(1) }
        awaitTag("action-pin-course")
        composeRule.onNodeWithTag("action-pin-course").performClick()
        awaitTag("storage-cap-warning")
        composeRule.onNodeWithTag("storage-cap-warning").assertIsDisplayed()
    }

    // --- Upload journeys (QMOB-E7-S3..S5) are PENDING -------------------------------------------
    // The mobile upload contract (QMOB-E1-S8) is fixed on the backend first. Share-to-Quire, the
    // upload queue and upload status are specified in docs/e2e/E7-sync.md and arrive here as their own
    // cases Ignored with reason QMOB-E7-upload — not added by this story (QMOB-E7-S0).

    // --- Helpers --------------------------------------------------------------------------------

    /** Enqueues the launch course list for `GET /courses`. */
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

    /**
     * Enqueues the responses the periodic sync worker refetches, with the files list now carrying the
     * extra 'Lecture 2' file. The worker refetches the whole list (`GET /courses`), then each course in list
     * display order (detail, sessions, files, notes); only 'Signals and Systems' has fixtures.
     */
    private fun enqueueSyncBundle() {
        server.enqueueFixture("courses-list.json")
        // The worker walks the cached courses in list display order (newest semester, then name): the Spring
        // course first, then Signals and Systems, then the Hebrew one. Only Signals has fixtures; the others 404
        // (one response each: a failed detail stops that course) so the worker finishes, not waits on the queue.
        server.enqueueFixture("error-not-found.json", code = 404)
        server.enqueueFixture("course-detail.json")
        server.enqueueFixture("course-sessions.json")
        server.enqueueFixture("course-files-synced.json")
        server.enqueueFixture("course-notes.json")
        server.enqueueFixture("error-not-found.json", code = 404)
    }

    /** Enqueues the 5-page PDF deck as a full `200` content body (Range/ETag per the contract). */
    private fun enqueuePdf() {
        server.enqueuePdfFixture(DECK_PDF)
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

    /** Switches the open course to its Files tab and waits for the list. */
    private fun selectFilesTab() {
        composeRule.onNodeWithTag("tab-files").performClick()
        awaitTag("file-list")
    }

    /**
     * Runs the app-scheduled periodic metadata sync worker now, via the WorkManager test driver, instead
     * of on its real interval. The app schedules it under [SYNC_UNIQUE_WORK] at launch (QMOB-E7-S1).
     */
    private fun runPeriodicSync() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork(SYNC_UNIQUE_WORK)
                .get()
                .first { !it.state.isFinished }
        val driver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        // The periodic work is constrained (network, battery not low): meet those, then release its period.
        driver.setAllConstraintsMet(info.id)
        driver.setPeriodDelayMet(info.id)
        composeRule.waitForIdle()
    }

    /**
     * Meets the network constraint on the one-time download the pinned-course logic enqueues for [fileId]
     * (keyed by the E5 [WorkManagerDownloadScheduler.uniqueName]), so it runs under the test driver.
     */
    private fun meetDownloadConstraints(fileId: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork(WorkManagerDownloadScheduler.uniqueName(fileId))
                .get()
                .first()
        requireNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setAllConstraintsMet(info.id)
        composeRule.waitForIdle()
    }

    /** Waits until the download for [fileId] has fully landed on disk (its Room row is DONE). */
    private fun awaitDownloaded(fileId: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            runBlocking { db.downloadDao().get(fileId)?.state } == DownloadEntity.DONE
        }
    }

    /** Waits until at least one node with [tag] is present (async Room/network/render settle). */
    private fun awaitTag(tag: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * WorkManager instantiates workers through this. It chains the current case's factories (the E5
     * [DownloadWorkerFactory] and the E7 [SyncWorkerFactory]): the first one that recognises the worker class
     * builds it; a factory returning `null` falls through to the next, then to WorkManager's default.
     */
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

        val SYNC_CONTROL_TAGS =
            listOf(
                "setting-sync-enabled",
                "setting-sync-interval",
                "setting-sync-wifi-only",
                "sync-last-success",
                "action-sync-now",
            )

        const val TIMEOUT_MS = 10_000L
        const val DECK_PDF = "lecture-1.pdf"

        /** Unique name the app schedules the periodic metadata sync under (QMOB-E7-S1). */
        const val SYNC_UNIQUE_WORK = "quire-sync"

        /** The file added on the server between the initial open and the sync (in `course-files-synced.json`). */
        const val NEW_FILE_ID = "b2c3d4e5-6f70-4a81-9b2c-3d4e5f607182"
    }
}
