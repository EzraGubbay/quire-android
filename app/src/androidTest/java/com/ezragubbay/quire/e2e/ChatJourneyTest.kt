package com.ezragubbay.quire.e2e

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ezragubbay.quire.MainActivity
import com.ezragubbay.quire.testing.MockWebServerRule
import com.ezragubbay.quire.testing.SessionSeedRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Epic e2e journey for QMOB-E6 (AI chat: thread lists, the streaming conversation, citations,
 * failure/retry, ask-about-this-document). Defined before any E6 story is implemented (QMOB-E6-S0,
 * #43): every case ships Ignored with reason QMOB-E6 and is un-ignored by the story (#44–#49) that
 * satisfies it. The epic is done when no such Ignore remains here (a release PR into `main` fails
 * while any remains).
 *
 * (This KDoc deliberately avoids the literal annotation text so the release-gate grep over
 * `app/src/androidTest` matches only the real annotations, not this note — the false-match lesson
 * carried from E2's PR #78/#79 through E3/E4/E5.)
 *
 * The journey finds UI by **test tag only** (the contract in `docs/e2e/E6-chat.md`), never by
 * display text — RTL-safe for Hebrew thread titles and for formulae in answers — and runs against
 * [MockWebServerRule] fixtures whose shapes mirror the real `mobile-v1` chat contract (merged in E1):
 *  - `GET /threads` — recent threads across courses ([CHAT_THREADS_RECENT]);
 *  - `GET /courses/{course}/threads[?documentId=]` — a course's threads, document-scoped when filtered;
 *  - `POST /courses/{course}/threads` → `201` a new thread ([CHAT_THREAD_CREATED]);
 *  - `GET /courses/{course}/threads/{id}/messages` — history ([CHAT_MESSAGES_HISTORY] / empty);
 *  - `POST .../messages` — the answer streams as **NDJSON** (`application/x-ndjson`), one event per
 *    line `type: user | delta | done | error`, delivered chunked + throttled by
 *    [MockWebServerRule.enqueueNdjsonFixture] so deltas arrive incrementally. Citations carry no
 *    web-only `href`. A mid-stream `error` event keeps HTTP `200`; a pre-stream failure uses the JSON
 *    error envelope (503 `ai_unavailable`, 429 `rate_limited`).
 * The seeded session ([SessionSeedRule] `signedIn = true`) points the app at [MockWebServerRule.baseUrl],
 * so the whole journey is hermetic.
 *
 * Each case awaits a **content** node (a populated `thread-list`, the settled `answer-markdown`, a
 * rendered `pdf-page-{n}`) — never a bare screen-root tag that is also present during Loading — so
 * later impl stories' cases do not flake at the CI gate. Streaming cases especially `waitUntil` the
 * settled answer, not a loading-time root.
 *
 * Journey: open the global Chats tab → thread list (and a course's Ask entry point → that course's
 * threads) → open or create a conversation → type a question → send → the user bubble appears and the
 * assistant answer grows incrementally (NDJSON delta → done) → stop-generation keeps the partial →
 * Markdown and block math render → tap a citation chip → the source opens in the viewer at the cited
 * page → a failure (mid-stream drop, budget reached, AI unavailable, offline) is shown with the right
 * action (Retry, or none for budget) and retry succeeds → from the viewer, 'Ask about this document'
 * opens the document-scoped chat (reusing the existing document thread).
 *
 * Case → owning E6 story (the story that removes the Ignore):
 *  - #44 (S1, chat data layer: NDJSON → Flow<ChatEvent>): underpins every streaming/thread case
 *    (repository + parser) but un-ignores none directly; verified by its own unit tests
 *    (`NdjsonParserTest`, `ChatRepositoryTest`).
 *  - #45 (S2, thread lists): Chats tab list, course 'Ask' → course threads, new-thread, offline list.
 *  - #46 (S3, conversation): user bubble + incremental streamed answer, stop, Markdown + math.
 *  - #47 (S4, citations): citation chip opens the viewer at the cited page; the Sources row.
 *  - #48 (S5, failures): mid-stream error + Retry (retry succeeds), budget (no Retry), AI unavailable,
 *    offline composer banner.
 *  - #49 (S6, ask about this document): viewer 'Ask' opens the document-scoped chat (the last Ignore
 *    removed, closing the epic); document threads carry a badge in the list.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ChatJourneyTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    // The hermetic server starts first so the seeded session can point at it (chat data streams from it).
    @get:Rule(order = 1)
    val server = MockWebServerRule(answerSessionCheckOutOfBand = true)

    // The chat surface is only reachable from a paired (signed-in) install.
    @get:Rule(order = 2)
    val session = SessionSeedRule(signedIn = true, serverUrl = { server.baseUrl })

    @get:Rule(order = 3)
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun closeScenario() {
        scenario?.close()
    }

    // --- Thread lists: un-ignored by QMOB-E6-S2 (#45) -------------------------------------------

    // un-ignored by QMOB-E6-S2 (#45)
    @Test
    @Ignore("QMOB-E6")
    fun chatsTab_listsRecentThreads() {
        enqueueCourses()
        server.enqueueFixture(CHAT_THREADS_RECENT) // GET /threads
        launch()
        openChatsTab()

        // The Chats tab lists recent threads across all courses, newest first (await a populated list).
        awaitTag("thread-list")
        composeRule.onNodeWithTag("screen-chats").assertIsDisplayed()
        composeRule.onNodeWithTag("thread-list").assertIsDisplayed()
        composeRule.onNodeWithTag("thread-row-$THREAD_ID").assertIsDisplayed()
    }

    // un-ignored by QMOB-E6-S2 (#45)
    @Test
    @Ignore("QMOB-E6")
    fun courseScreen_askEntryPoint_opensThatCoursesThreads() {
        enqueueCourses()
        enqueueCourseDetail()
        server.enqueueFixture(CHAT_THREADS_COURSE) // GET /courses/{course}/threads
        launch()
        openSignalsCourse()

        // The course screen's 'Ask' opens that course's threads.
        composeRule.onNodeWithTag("action-ask").performClick()
        awaitTag("thread-list")
        composeRule.onNodeWithTag("thread-row-$THREAD_ID").assertIsDisplayed()
    }

    // un-ignored by QMOB-E6-S2 (#45)
    @Test
    @Ignore("QMOB-E6")
    fun chatsTab_newThread_opensEmptyConversation() {
        enqueueCourses()
        server.enqueueFixture(CHAT_THREADS_RECENT)
        server.enqueueFixture(CHAT_THREAD_CREATED, code = 201) // POST /courses/{course}/threads
        server.enqueueFixture(CHAT_MESSAGES_EMPTY) // GET .../messages for the fresh thread
        launch()
        openChatsTab()
        awaitTag("thread-list")

        // 'New chat' creates a thread and drops into an empty conversation with a ready composer.
        composeRule.onNodeWithTag("action-new-thread").performClick()
        awaitTag("chat-input")
        composeRule.onNodeWithTag("screen-conversation").assertIsDisplayed()
        composeRule.onNodeWithTag("chat-input").assertIsDisplayed()
    }

    // un-ignored by QMOB-E6-S2 (#45)
    @Test
    @Ignore("QMOB-E6")
    fun chatsTab_offline_showsCachedListWithBanner() {
        enqueueCourses()
        server.enqueueFixture(CHAT_THREADS_RECENT) // one success populates the Room cache
        launch()
        openChatsTab()
        awaitTag("thread-list")

        // With the server gone the cached threads still render, behind a read-only offline banner.
        server.server.shutdown()
        relaunch()
        openChatsTab()
        awaitTag("chat-offline-banner")
        composeRule.onNodeWithTag("thread-row-$THREAD_ID").assertIsDisplayed()
        composeRule.onNodeWithTag("chat-offline-banner").assertIsDisplayed()
    }

    // --- Conversation: streaming, stop, Markdown + math: QMOB-E6-S3 (#46) -----------------------

    // un-ignored by QMOB-E6-S3 (#46)
    @Test
    @Ignore("QMOB-E6")
    fun conversation_send_showsUserBubble_thenStreamsAnswerIncrementally() {
        openThreadReadyToSend()

        // Typing a question and sending shows the user bubble (UserSaved), then the assistant answer
        // grows from NDJSON deltas and settles on `done` — await the settled Markdown body, not a root.
        composeRule.onNodeWithTag("chat-input").performTextInput("What is convolution?")
        composeRule.onNodeWithTag("action-send").performClick()
        awaitTag("message-user-0")
        awaitTag("answer-markdown")
        composeRule.onNodeWithTag("message-user-0").assertIsDisplayed()
        composeRule.onNodeWithTag("message-assistant-1").assertIsDisplayed()
    }

    // un-ignored by QMOB-E6-S3 (#46)
    @Test
    @Ignore("QMOB-E6")
    fun conversation_stopGeneration_keepsPartialAnswer() {
        openThreadReadyToSend()
        composeRule.onNodeWithTag("chat-input").performTextInput("What is convolution?")
        composeRule.onNodeWithTag("action-send").performClick()

        // Once the assistant bubble appears mid-stream, Stop cancels the call and keeps the partial text.
        awaitTag("message-assistant-1")
        composeRule.onNodeWithTag("action-stop").performClick()
        composeRule.onNodeWithTag("message-assistant-1").assertIsDisplayed()
    }

    // un-ignored by QMOB-E6-S3 (#46)
    @Test
    @Ignore("QMOB-E6")
    fun conversation_rendersMarkdownAndMath() {
        enqueueCourses()
        server.enqueueFixture(CHAT_THREADS_RECENT)
        server.enqueueFixture(CHAT_MESSAGES_EMPTY)
        server.enqueueNdjsonFixture(CHAT_STREAM_MARKDOWN) // answer with a heading, list, code and block math
        launch()
        openChatsTab()
        awaitTag("thread-list")
        composeRule.onNodeWithTag("thread-row-$THREAD_ID").performClick()
        awaitTag("chat-input")
        composeRule.onNodeWithTag("chat-input").performTextInput("Explain convolution with the formula")
        composeRule.onNodeWithTag("action-send").performClick()

        // The answer renders Markdown (via the ADR 0002 Markwon approach) and surfaces block LaTeX as
        // its own displayed, taggable node (the same contract as the notes reader's `note-math`).
        awaitTag("answer-markdown")
        composeRule.onNodeWithTag("answer-markdown").assertIsDisplayed()
        composeRule.onNodeWithTag("answer-math").assertIsDisplayed()
    }

    // --- Citations: chips open the source at the cited page: QMOB-E6-S4 (#47) -------------------

    // un-ignored by QMOB-E6-S4 (#47)
    @Test
    @Ignore("QMOB-E6")
    fun answer_citationChip_opensViewerAtCitedPage() {
        openThreadReadyToSend()
        composeRule.onNodeWithTag("chat-input").performTextInput("What is convolution?")
        composeRule.onNodeWithTag("action-send").performClick()
        awaitTag("answer-markdown")

        // The `[1]` marker is a tappable chip; tapping it opens the cited document in the viewer at its
        // page (pageNo 7) through the open-file flow, which streams the PDF content on demand.
        server.enqueuePdfFixture(DECK_PDF)
        composeRule.onNodeWithTag("citation-chip-1").performClick()
        awaitTag("pdf-page-$CITED_PAGE")
        composeRule.onNodeWithTag("screen-pdf-viewer").assertIsDisplayed()
        composeRule.onNodeWithTag("pdf-page-$CITED_PAGE").assertIsDisplayed()
    }

    // un-ignored by QMOB-E6-S4 (#47)
    @Test
    @Ignore("QMOB-E6")
    fun answer_sourcesRow_listsCitedSource() {
        openThreadReadyToSend()
        composeRule.onNodeWithTag("chat-input").performTextInput("What is convolution?")
        composeRule.onNodeWithTag("action-send").performClick()
        awaitTag("answer-markdown")

        // A Sources row under the answer lists each cited item (title + page).
        awaitTag("sources-row")
        composeRule.onNodeWithTag("sources-row").assertIsDisplayed()
        composeRule.onNodeWithTag("source-row-1").assertIsDisplayed()
    }

    // --- Failures, retry, offline: QMOB-E6-S5 (#48) ---------------------------------------------

    // un-ignored by QMOB-E6-S5 (#48)
    @Test
    @Ignore("QMOB-E6")
    fun stream_errorMidStream_keepsPartial_showsRetry_retrySucceeds() {
        enqueueCourses()
        server.enqueueFixture(CHAT_THREADS_RECENT)
        server.enqueueFixture(CHAT_MESSAGES_EMPTY)
        server.enqueueNdjsonFixture(CHAT_STREAM_ERROR_MIDSTREAM) // delta then a provider `error` event
        launch()
        openChatsTab()
        awaitTag("thread-list")
        composeRule.onNodeWithTag("thread-row-$THREAD_ID").performClick()
        awaitTag("chat-input")
        composeRule.onNodeWithTag("chat-input").performTextInput("What is convolution?")
        composeRule.onNodeWithTag("action-send").performClick()

        // The partial text is kept and an error with a Retry action is shown...
        awaitTag("chat-error")
        composeRule.onNodeWithTag("message-assistant-1").assertIsDisplayed()
        composeRule.onNodeWithTag("action-retry").assertIsDisplayed()

        // ...retry sends `{ retry: true }` (no duplicate user bubble) and the fresh stream completes.
        server.enqueueNdjsonFixture(CHAT_STREAM_NORMAL)
        composeRule.onNodeWithTag("action-retry").performClick()
        awaitTag("answer-markdown")
        composeRule.onNodeWithTag("answer-markdown").assertIsDisplayed()
    }

    // un-ignored by QMOB-E6-S5 (#48)
    @Test
    @Ignore("QMOB-E6")
    fun stream_budgetReached_showsError_withoutRetry() {
        enqueueCourses()
        server.enqueueFixture(CHAT_THREADS_RECENT)
        server.enqueueFixture(CHAT_MESSAGES_EMPTY)
        server.enqueueNdjsonFixture(CHAT_STREAM_ERROR_BUDGET) // monthly AI budget reached — terminal
        launch()
        openChatsTab()
        awaitTag("thread-list")
        composeRule.onNodeWithTag("thread-row-$THREAD_ID").performClick()
        awaitTag("chat-input")
        composeRule.onNodeWithTag("chat-input").performTextInput("Summarise the whole course")
        composeRule.onNodeWithTag("action-send").performClick()

        // Budget is terminal: the error is shown with no Retry action.
        awaitTag("chat-error")
        composeRule.onNodeWithTag("chat-error").assertIsDisplayed()
        composeRule.onNodeWithTag("action-retry").assertDoesNotExist()
    }

    // un-ignored by QMOB-E6-S5 (#48)
    @Test
    @Ignore("QMOB-E6")
    fun preStream_aiUnavailable_showsError() {
        enqueueCourses()
        server.enqueueFixture(CHAT_THREADS_RECENT)
        server.enqueueFixture(CHAT_MESSAGES_EMPTY)
        server.enqueueFixture(ERROR_AI_UNAVAILABLE, code = 503) // 503 before the stream begins
        launch()
        openChatsTab()
        awaitTag("thread-list")
        composeRule.onNodeWithTag("thread-row-$THREAD_ID").performClick()
        awaitTag("chat-input")
        composeRule.onNodeWithTag("chat-input").performTextInput("What is convolution?")
        composeRule.onNodeWithTag("action-send").performClick()

        // A 503 `ai_unavailable` (AI not configured on the server) surfaces as a chat error.
        awaitTag("chat-error")
        composeRule.onNodeWithTag("chat-error").assertIsDisplayed()
    }

    // un-ignored by QMOB-E6-S5 (#48)
    @Test
    @Ignore("QMOB-E6")
    fun conversation_offline_disablesComposer_showsBanner() {
        enqueueCourses()
        server.enqueueFixture(CHAT_THREADS_RECENT)
        server.enqueueFixture(CHAT_MESSAGES_HISTORY)
        launch()
        openChatsTab()
        awaitTag("thread-list")
        composeRule.onNodeWithTag("thread-row-$THREAD_ID").performClick()
        awaitTag("message-assistant-1")

        // Going offline disables the composer and shows a banner (questions cannot be sent offline).
        server.server.shutdown()
        awaitTag("chat-offline-banner")
        composeRule.onNodeWithTag("chat-offline-banner").assertIsDisplayed()
    }

    // --- Ask about this document: QMOB-E6-S6 (#49) ----------------------------------------------

    // un-ignored by QMOB-E6-S6 (#49) — removes the last Ignore, closing the epic
    @Test
    @Ignore("QMOB-E6")
    fun viewer_askAboutDocument_opensDocumentScopedChat() {
        enqueueCourses()
        enqueueCourseDetail()
        server.enqueuePdfFixture(DECK_PDF) // viewing the deck streams it to the viewer cache
        launch()
        openLectureFile()
        awaitTag("pdf-page-1")

        // 'Ask about this document' reuses the most recent document thread (via ?documentId=) rather
        // than creating a duplicate, and opens the conversation with a header naming the document.
        server.enqueueFixture(CHAT_THREADS_DOCUMENT) // GET /courses/{course}/threads?documentId=
        server.enqueueFixture(CHAT_MESSAGES_HISTORY)
        composeRule.onNodeWithTag("action-ask-about-doc").performClick()
        awaitTag("conversation-doc-header")
        composeRule.onNodeWithTag("screen-conversation").assertIsDisplayed()
        composeRule.onNodeWithTag("conversation-doc-header").assertIsDisplayed()
    }

    // un-ignored by QMOB-E6-S6 (#49)
    @Test
    @Ignore("QMOB-E6")
    fun threadList_documentThread_carriesDocumentBadge() {
        enqueueCourses()
        server.enqueueFixture(CHAT_THREADS_RECENT) // includes a document-scoped thread
        launch()
        openChatsTab()
        awaitTag("thread-list")

        // A document-scoped thread is marked with a document badge in the list.
        composeRule.onNodeWithTag("thread-row-$DOC_THREAD_ID").assertIsDisplayed()
        composeRule.onNodeWithTag("thread-doc-badge").assertIsDisplayed()
    }

    // --- Helpers --------------------------------------------------------------------------------

    /** Enqueues the launch course list (`GET /courses`), fetched when the signed-in app starts. */
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
     * Opens an existing thread with an empty history and a normal streaming answer queued, leaving the
     * composer ready to send — the common setup for the streaming / citation cases.
     */
    private fun openThreadReadyToSend() {
        enqueueCourses()
        server.enqueueFixture(CHAT_THREADS_RECENT)
        server.enqueueFixture(CHAT_MESSAGES_EMPTY)
        server.enqueueNdjsonFixture(CHAT_STREAM_NORMAL)
        launch()
        openChatsTab()
        awaitTag("thread-list")
        composeRule.onNodeWithTag("thread-row-$THREAD_ID").performClick()
        awaitTag("chat-input")
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
        composeRule.waitForIdle()
    }

    /** Switches to the global Chats tab in the bottom bar. */
    private fun openChatsTab() {
        composeRule.onNodeWithTag("nav-chats").performClick()
    }

    /** Opens the Signals and Systems course from the list and waits for its screen. */
    private fun openSignalsCourse() {
        composeRule.onNodeWithTag("course-card-$COURSE_SLUG").performClick()
        awaitTag("screen-course")
    }

    /** Opens the course's Files tab and taps the lecture deck row to drive the open-file flow. */
    private fun openLectureFile() {
        openSignalsCourse()
        composeRule.onNodeWithTag("tab-files").performClick()
        awaitTag("file-list")
        composeRule.onNodeWithTag("file-row-$LECTURE_FILE_ID").performClick()
    }

    /** Waits until at least one node with [tag] is present (async Room / network / stream settle). */
    private fun awaitTag(tag: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val COURSE_SLUG = "signals-and-systems"
        const val THREAD_ID = "a3c5d7e9-1b2d-4f60-9a8b-7c6d5e4f3a2b"
        const val DOC_THREAD_ID = "b4d6e8f0-2c3e-4a71-8b9c-6d5e4f3a2b1c"
        const val LECTURE_FILE_ID = "6f1c2a52-8b0e-4c6a-9d63-2a3a7c1d9e10"
        const val CITED_PAGE = 7
        const val DECK_PDF = "lecture-1.pdf"

        const val CHAT_THREADS_RECENT = "chat-threads-recent.json"
        const val CHAT_THREADS_COURSE = "chat-threads-course.json"
        const val CHAT_THREADS_DOCUMENT = "chat-threads-document.json"
        const val CHAT_THREAD_CREATED = "chat-thread-created.json"
        const val CHAT_MESSAGES_EMPTY = "chat-messages-empty.json"
        const val CHAT_MESSAGES_HISTORY = "chat-messages-history.json"
        const val CHAT_STREAM_NORMAL = "chat-stream-normal.ndjson"
        const val CHAT_STREAM_MARKDOWN = "chat-stream-markdown.ndjson"
        const val CHAT_STREAM_ERROR_MIDSTREAM = "chat-stream-error-midstream.ndjson"
        const val CHAT_STREAM_ERROR_BUDGET = "chat-stream-error-budget.ndjson"
        const val ERROR_AI_UNAVAILABLE = "error-ai-unavailable.json"
    }
}
