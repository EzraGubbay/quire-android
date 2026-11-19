package com.ezragubbay.quire.e2e

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ezragubbay.quire.MainActivity
import com.ezragubbay.quire.testing.MockWebServerRule
import com.ezragubbay.quire.testing.SessionSeedRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Epic e2e journey for QMOB-E4 (browse & read: the offline-first course/file browsing surface).
 * Defined before any E4 story is implemented (QMOB-E4-S0, #29): every case ships Ignored with
 * reason QMOB-E4 and is un-ignored by the story (#30–#34) that satisfies it. The epic is done when
 * no such Ignore remains here (a release PR into `main` fails while any remains).
 *
 * (This KDoc deliberately avoids the literal annotation text so the release-gate grep over
 * `app/src/androidTest` matches only the real annotations, not this note — the false-match lesson
 * from E2's PR #78/#79.)
 *
 * The journey finds UI by **test tag only** (the contract in `docs/e2e/E4-browse.md`), never by
 * display text — RTL-safe for Hebrew course names — and runs against [MockWebServerRule] fixtures
 * whose shapes mirror the real `mobile-v1` browse contract (`GET /courses`, `GET /courses/{course}`,
 * `.../sessions`, `.../files`, `.../notes`; error envelope `{ "error": { "code", "message" } }`).
 * The seeded session ([SessionSeedRule] `signedIn = true`) points the app at [MockWebServerRule.baseUrl],
 * so the whole journey is hermetic.
 *
 * Journey: signed-in launch → Courses shows a semester-grouped list from `GET /courses` →
 * pull-to-refresh refetches → open a course → the course screen shows Sessions/Files/Notes tabs,
 * Sessions first with Lecture 1 and its deck → Files tab labels each file by type, and the
 * Recitation filter chip narrows the list → a file-info sheet shows size and available formats →
 * search from Courses finds a file by title → relaunch with the server down still renders the
 * cached courses behind an offline banner (offline-first).
 *
 * Case → owning E4 story (the story that removes the Ignore):
 *  - #31 (S2, Courses screen): list, pull-to-refresh, offline-cached.
 *  - #32 (S3, Course screen tabs): course-open/sessions, tabs, notes.
 *  - #33 (S4, Files tab): recitation filter, type chips + sort, file-info sheet.
 *  - #34 (S5, Search): search-by-title — the last Ignore removed, closing the epic.
 *  - #30 (S1, offline-first data layer) underpins every case (Room cache + repositories) but
 *    un-ignores none directly; it is verified by its own unit/contract tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CoursesJourneyTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    // The hermetic server starts first so the seeded session can point at it (browse data is fetched from it).
    @get:Rule(order = 1)
    val server = MockWebServerRule(answerSessionCheckOutOfBand = true)

    // The browse surface is only shown to a paired (signed-in) install.
    @get:Rule(order = 2)
    val session = SessionSeedRule(signedIn = true, serverUrl = { server.baseUrl })

    @get:Rule(order = 3)
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun closeScenario() {
        scenario?.close()
    }

    // --- Courses list: un-ignored by QMOB-E4-S2 (#31) -------------------------------------------

    // un-ignored by QMOB-E4-S2 (#31)
    @Test
    fun signedInLaunch_showsSemesterGroupedCourseList() {
        enqueueCourses()
        launch()

        // Courses is the shell start destination; its list is grouped under sticky semester headers.
        composeRule.onNodeWithTag("screen-courses").assertIsDisplayed()
        awaitTag("course-list") // the list replaces the skeleton once GET /courses lands
        composeRule.onNodeWithTag("course-list").assertIsDisplayed()
        composeRule.onNodeWithTag("course-group-winter-2026").assertIsDisplayed()
        composeRule.onNodeWithTag("course-group-spring-2026").assertIsDisplayed()
        // Cards are keyed by slug (RTL Hebrew names are found by tag, never by text).
        composeRule.onNodeWithTag("course-card-signals-and-systems").assertIsDisplayed()
        awaitTag("course-card-infi-1")
        composeRule
            .onNodeWithTag("course-list")
            .performScrollToNode(hasTestTag("course-card-intro-to-ml"))
        composeRule.onNodeWithTag("course-card-intro-to-ml").assertIsDisplayed()
    }

    // un-ignored by QMOB-E4-S2 (#31)
    @Test
    fun coursesList_pullToRefresh_refetchesFromServer() {
        enqueueCourses()
        launch()
        awaitTag("course-list")
        composeRule.onNodeWithTag("course-list").assertIsDisplayed()
        takeCoursesRequest() // the first GET /courses on launch

        // Pull-to-refresh issues a second GET /courses and upserts the cache.
        enqueueCourses()
        // Start inside the node (not on its top edge, where the injected gesture never reaches the pull handler).
        composeRule
            .onNodeWithTag("courses-refresh")
            .performTouchInput { swipeDown(startY = height * 0.1f, endY = height * 0.8f, durationMillis = 500) }

        val refresh = takeCoursesRequest()
        assertTrue("Pull-to-refresh must GET /courses again", refresh.path!!.endsWith("/courses"))
        assertTrue("Refresh must be a GET", refresh.method == "GET")
        composeRule.onNodeWithTag("course-list").assertIsDisplayed()
    }

    // un-ignored by QMOB-E4-S2 (#31)
    @Test
    fun relaunchWithServerDown_showsCachedCourses_withOfflineBanner() {
        // First launch populates the Room cache from the server.
        enqueueCourses()
        launch()
        awaitTag("course-card-signals-and-systems")
        composeRule.onNodeWithTag("course-card-signals-and-systems").assertIsDisplayed()

        // The server goes away; a cold relaunch must still render the cached list offline-first,
        // with an unobtrusive offline banner over the cached data.
        server.server.shutdown()
        relaunch()
        composeRule.waitForIdle()

        // The cached list renders from Room; the failed refresh then raises the offline banner.
        awaitTag("offline-banner")
        composeRule.onNodeWithTag("screen-courses").assertIsDisplayed()
        composeRule.onNodeWithTag("course-list").assertIsDisplayed()
        composeRule.onNodeWithTag("course-card-signals-and-systems").assertIsDisplayed()
        composeRule.onNodeWithTag("offline-banner").assertIsDisplayed()
    }

    // --- Course screen tabs: un-ignored by QMOB-E4-S3 (#32) -------------------------------------

    // un-ignored by QMOB-E4-S3 (#32)
    @Test
    fun tapCourseCard_opensCourse_onSessionsTab_showsLectureOneWithDeck() {
        enqueueCourses()
        enqueueCourseDetail()
        launch()
        openSignalsCourse()

        // The course screen opens on the Sessions tab: a dated timeline of lectures/recitations.
        composeRule.onNodeWithTag("screen-course").assertIsDisplayed()
        composeRule.onNodeWithTag("course-tabs").assertIsDisplayed()
        composeRule.onNodeWithTag("tab-sessions").assertIsSelected()
        composeRule.onNodeWithTag("session-row-lecture-1").assertIsDisplayed()
        // Lecture 1 carries its deck; tapping it emits an open-file event (the viewer arrives in E5).
        composeRule
            .onNodeWithTag("session-deck-$LECTURE_FILE_ID")
            .assertIsDisplayed()
    }

    // un-ignored by QMOB-E4-S3 (#32)
    @Test
    fun courseScreen_exposesSessionsFilesNotesTabs() {
        enqueueCourses()
        enqueueCourseDetail()
        launch()
        openSignalsCourse()

        composeRule.onNodeWithTag("tab-sessions").assertIsDisplayed()
        composeRule.onNodeWithTag("tab-files").assertIsDisplayed()
        composeRule.onNodeWithTag("tab-notes").assertIsDisplayed()

        composeRule.onNodeWithTag("tab-files").performClick()
        composeRule.onNodeWithTag("file-list").assertIsDisplayed()

        composeRule.onNodeWithTag("tab-notes").performClick()
        composeRule.onNodeWithTag("note-list").assertIsDisplayed()
    }

    // un-ignored by QMOB-E4-S3 (#32)
    @Test
    fun courseScreen_notesTab_listsCachedNotes() {
        enqueueCourses()
        enqueueCourseDetail()
        launch()
        openSignalsCourse()

        // Notes come from GET /courses/{course}/notes, cached in Room and read-only here.
        composeRule.onNodeWithTag("tab-notes").performClick()
        composeRule.onNodeWithTag("note-list").assertIsDisplayed()
        composeRule.onNodeWithTag("note-row-$LECTURE_NOTE_ID").assertIsDisplayed()
    }

    // --- Files tab: un-ignored by QMOB-E4-S4 (#33) ----------------------------------------------

    // un-ignored by QMOB-E4-S4 (#33)
    @Ignore("QMOB-E4")
    @Test
    fun filesTab_recitationFilterChip_narrowsList() {
        enqueueCourses()
        enqueueCourseDetail()
        launch()
        openSignalsCourse()
        composeRule.onNodeWithTag("tab-files").performClick()

        // Before filtering, both a lecture file and a recitation file are listed.
        composeRule.onNodeWithTag("file-row-$LECTURE_FILE_ID").assertIsDisplayed()
        composeRule.onNodeWithTag("file-row-$RECITATION_FILE_ID").assertIsDisplayed()

        // Selecting the Recitation filter chip narrows the list to recitation files only.
        composeRule.onNodeWithTag("filter-chip-recitation").performClick()
        composeRule.onNodeWithTag("file-row-$RECITATION_FILE_ID").assertIsDisplayed()
        composeRule.onNodeWithTag("file-row-$LECTURE_FILE_ID").assertDoesNotExist()
    }

    // un-ignored by QMOB-E4-S4 (#33)
    @Ignore("QMOB-E4")
    @Test
    fun filesTab_showsTypeChipsAndSortControl() {
        enqueueCourses()
        enqueueCourseDetail()
        launch()
        openSignalsCourse()
        composeRule.onNodeWithTag("tab-files").performClick()

        // Each file row carries a type-label chip; a sort control is available.
        composeRule
            .onAllNodesWithTag("file-type-chip")[0]
            .assertIsDisplayed()
        composeRule.onNodeWithTag("sort-control").assertIsDisplayed()
    }

    // un-ignored by QMOB-E4-S4 (#33)
    @Ignore("QMOB-E4")
    @Test
    fun filesTab_openFileInfoSheet_showsFormatsAndSize() {
        enqueueCourses()
        enqueueCourseDetail()
        launch()
        openSignalsCourse()
        composeRule.onNodeWithTag("tab-files").performClick()

        // Long-press a file row opens the info bottom sheet (full title, type, pages, size, formats).
        composeRule.onNodeWithTag("file-row-$LECTURE_FILE_ID").performTouchInput { longClick() }
        composeRule.onNodeWithTag("file-info-sheet").assertIsDisplayed()
    }

    // --- Search: un-ignored by QMOB-E4-S5 (#34) — the last Ignore removed, closing the epic -----

    // un-ignored by QMOB-E4-S5 (#34)
    @Ignore("QMOB-E4")
    @Test
    fun search_fromCourses_findsFileByTitle() {
        enqueueCourses()
        enqueueCourseDetail() // search is served from Room after one successful sync, offline-capable
        launch()
        openSignalsCourse()
        // Return to Courses so the search action is in scope, then open full-screen search.
        composeRule.onNodeWithTag("nav-courses").performClick()
        composeRule.onNodeWithTag("action-search").performClick()
        composeRule.onNodeWithTag("screen-search").assertIsDisplayed()

        // Typing part of a file title surfaces it under the Files section (case-insensitive, over Room).
        composeRule.onNodeWithTag("search-field").performTextInput("Convolution")
        awaitTag("search-result-file-$LECTURE_FILE_ID")
        composeRule.onNodeWithTag("search-section-files").assertIsDisplayed()
        composeRule.onNodeWithTag("search-result-file-$LECTURE_FILE_ID").assertIsDisplayed()
    }

    // --- Helpers --------------------------------------------------------------------------------

    /** Enqueues the course list for the launch `GET /courses`. */
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

    /** The next recorded `GET .../courses` request, skipping the session check. */
    private fun takeCoursesRequest(): RecordedRequest {
        while (true) {
            val request = server.takeRequest()
            if (request.path!!.endsWith("/courses")) return request
        }
    }

    /** Launches [MainActivity] (signed-in: lands on the Courses shell). */
    private fun launch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        composeRule.waitForIdle()
    }

    /** Recreates the running activity to model a cold relaunch (the Room cache must survive). */
    private fun relaunch() {
        scenario?.recreate()
    }

    /** Opens the Signals and Systems course from the list and waits for its screen. */
    private fun openSignalsCourse() {
        composeRule.onNodeWithTag("course-card-signals-and-systems").performClick()
        awaitTag("screen-course")
    }

    /** Waits until at least one node with [tag] is present (async Room/network settle). */
    private fun awaitTag(tag: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val LECTURE_FILE_ID = "6f1c2a52-8b0e-4c6a-9d63-2a3a7c1d9e10"
        const val RECITATION_FILE_ID = "7a2d3b63-9c1f-4d7b-ae74-3b4b8d2eaf21"
        const val LECTURE_NOTE_ID = "0d6f6c3e-4f5a-4b0b-8f0e-9a6b1d2c3e4f"
    }
}
