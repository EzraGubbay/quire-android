package com.ezragubbay.quire.e2e

import android.Manifest
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ezragubbay.quire.MainActivity
import com.ezragubbay.quire.core.data.session.SessionRepository
import com.ezragubbay.quire.core.network.TokenCache
import com.ezragubbay.quire.push.PushChannels
import com.ezragubbay.quire.push.PushHandleResult
import com.ezragubbay.quire.push.PushMessageHandler
import com.ezragubbay.quire.testing.FakeFcmTokenSource
import com.ezragubbay.quire.testing.MockWebServerRule
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Epic e2e journey for QMOB-E8 (push notifications, ADR 0003 §6). Written first, by QMOB-E8-S3 (#63), before any E8
 * implementation: every case shipped Ignored with reason QMOB-E8 and is un-ignored by the story that satisfies it
 * (#63 registration + receiving, #64 deep links, #65 preferences). The epic is done when no such Ignore remains here
 * (a release PR into `main` fails while any remains).
 *
 * (This KDoc deliberately avoids the literal annotation text so the release-gate grep over `app/src/androidTest`
 * matches only the real annotations, not this note.)
 *
 * Hermetic, like every journey: [MockWebServerRule] answers by **path** (see [installDispatcher]), so the app's own
 * background calls (app-start registration, name lookups) cannot steal a response queued for another request. There
 * is no Firebase here: no `google-services.json`, no real FCM. The app's `FcmTokenSource` is the [FakeFcmTokenSource]
 * (bound for every instrumented test with `@TestInstallIn`, off by default) which this class switches on, so it
 * hands out the fixed [FakeFcmTokenSource.TOKEN]. A "push" is injected by building
 * `RemoteMessage.Builder("quire@fcm.googleapis.com").setData(map).build()` and passing `.data` to the app's
 * [PushMessageHandler] — the same call `QuireMessagingService.onMessageReceived` makes. Notifications are asserted
 * through `NotificationManager.activeNotifications` (channel, tag) and opened by sending their `contentIntent` (see
 * [tap]: with the background-start opt-in a System UI tap carries, then waiting for `MainActivity` to resume). Every
 * tap case cold-starts the app except [filesAdded_tapWhileRunning_replacesBackStack], which taps while it runs.
 * `POST_NOTIFICATIONS` is granted up front ([GrantPermissionRule]).
 *
 * UI is found by **test tag only** (the contract in `docs/e2e/E8-push.md`).
 *
 * Case → owning E8 story (the story that removes the Ignore):
 *  - #63 (S3, registration + receiving): registration after pairing, files.added posts on `push_files`, an unknown
 *    type posts nothing, sign-out deletes the registration before revoking the token.
 *  - #64 (S4, deep links): every tap case.
 *  - #65 (S5, preferences): category off, send test notification.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PushJourneyTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val server = MockWebServerRule()

    // Android 13+ asks at runtime; the CI emulator is API 34. Granted up front, so no permission card shows.
    @get:Rule(order = 2)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 3)
    val composeRule = createEmptyComposeRule()

    @Inject lateinit var handler: PushMessageHandler

    @Inject lateinit var tokens: FakeFcmTokenSource

    @Inject lateinit var sessions: SessionRepository

    @Inject lateinit var tokenCache: TokenCache

    /** Paths answered 404 by the dispatcher (a deleted file for the stale-target case). */
    private val missingPaths = mutableSetOf<String>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    private var scenario: ActivityScenario<MainActivity>? = null

    /** An activity the test detached from [scenario]; tearDown finishes it even when an assertion failed first. */
    private var detached: MainActivity? = null

    @Before
    fun setUp() {
        hilt.inject()
        tokens.available = true
        installDispatcher()
        notificationManager.cancelAll()
    }

    @After
    fun tearDown() {
        scenario?.close()
        detached?.let { if (!it.isDestroyed) finishAndAwaitDestroyed(it) }
        detached = null
        notificationManager.cancelAll()
    }

    // --- Registration and receiving: un-ignored by QMOB-E8-S3 (#63) -------------------------------

    // un-ignored by QMOB-E8-S3 (#63)
    @Test
    fun registration_afterPairing_putsTokenAndCategories() {
        seedSignedOut()
        launch()

        pair()

        // Pairing registers this device: PUT /push/registration with the FCM token and every category on.
        val put = awaitRequest("PUT", "/push/registration")
        val body = JSONObject(put.body.readUtf8())
        assertEquals(FakeFcmTokenSource.TOKEN, body.getString("fcmToken"))
        assertEquals("android", body.getString("platform"))
        assertEquals(listOf("files", "deadlines", "notes"), body.getJSONArray("categories").strings())
        assertEquals("Bearer $PAIRED_TOKEN", put.getHeader("Authorization"))
    }

    // un-ignored by QMOB-E8-S3 (#63)
    @Test
    fun filesAdded_single_postsNotificationOnFilesChannel() {
        seedSignedIn()

        val result = inject(pushData("push-files-added.json"))

        assertTrue("expected a posted notification, got $result", result is PushHandleResult.Posted)
        val posted = awaitNotification(FILES_TAG)
        assertEquals(PushChannels.FILES, posted.notification.channelId)
        // The course name came from the API (GET /courses/{course}); payloads carry ids only.
        assertEquals(
            "Signals and Systems",
            posted.notification.extras
                .getCharSequence(Notification.EXTRA_TITLE)
                ?.toString(),
        )
    }

    // un-ignored by QMOB-E8-S3 (#63)
    @Test
    fun unknownType_postsNothing() {
        seedSignedIn()

        val result = inject(pushData("push-files-added.json") + ("type" to "course.archived"))

        assertEquals(PushHandleResult.Dropped, result)
        SystemClock.sleep(NOTHING_POSTED_GRACE_MS)
        assertTrue(pushNotifications().isEmpty())
    }

    // un-ignored by QMOB-E8-S3 (#63)
    @Test
    fun signOut_deletesRegistrationThenRevokes() {
        seedSignedIn()
        launch()
        // App start registers (signed in, push configured).
        awaitRequest("PUT", "/push/registration")

        composeRule.onNodeWithTag("action-settings").performClick()
        awaitTag("action-sign-out")
        composeRule.onNodeWithTag("action-sign-out").performScrollTo().performClick()
        awaitTag("screen-pair")

        // DELETE /push/registration goes out before DELETE /auth/token.
        val seen = mutableListOf<String>()
        while (seen.lastOrNull() != "DELETE /auth/token") {
            val request = requireNotNull(server.server.takeRequest(TIMEOUT_S, TimeUnit.SECONDS)) { "saw only $seen" }
            seen += "${request.method} ${request.path?.removePrefix(API_PREFIX)}"
        }
        val deleteRegistration = seen.indexOf("DELETE /push/registration")
        assertTrue("no DELETE /push/registration in $seen", deleteRegistration >= 0)
        assertTrue("registration must be deleted before the revoke: $seen", deleteRegistration < seen.lastIndex)

        // Then the local half: the FCM token is deleted and auto-init turned off.
        awaitCondition { tokens.autoInit == false }
        assertEquals(listOf("delete", "autoInit=false"), tokens.calls.takeLast(2))
    }

    // --- Deep links: un-ignored by QMOB-E8-S4 (#64) -------------------------------------------------

    // un-ignored by QMOB-E8-S4 (#64)
    @Test
    fun filesAdded_single_tapOpensViewer_backReturnsToCourse() {
        seedSignedIn()
        inject(pushData("push-files-added.json"))

        tap(FILES_TAG)

        awaitTag("screen-pdf-viewer")
        Espresso.pressBack()
        awaitTag("screen-course")
    }

    // un-ignored by QMOB-E8-S4 (#64)
    @Test
    fun filesAdded_many_tapOpensCourse() {
        seedSignedIn()
        inject(pushData("push-files-added.json") + ("count" to "4"))

        tap(FILES_TAG)

        awaitTag("screen-course")
        composeRule.onNodeWithTag("screen-pdf-viewer").assertDoesNotExist()
    }

    // Added by QMOB-E8-S4 (#64): a tap while the app runs reaches the same activity (onNewIntent) and replaces the
    // shell's back stack, so back walks target -> course -> Courses, not back into the tab that was open.
    @Test
    fun filesAdded_tapWhileRunning_replacesBackStack() {
        seedSignedIn()
        launch()
        awaitTag("screen-courses")
        composeRule.onNodeWithTag("nav-chats").performClick()
        awaitTag("screen-chats")
        val running = resumedMainActivity()
        assertNotNull(running)
        inject(pushData("push-files-added.json"))

        tap(FILES_TAG)
        // onNewIntent replaced the activity's intent (setIntent). ActivityScenario follows its activity by the intent
        // it launched it with, so from here on it ignores this activity's lifecycle and close() would wait for a
        // DESTROYED it never sees. The test finishes the activity itself (finishAndAwaitDestroyed) instead.
        scenario = null
        detached = running

        awaitTag("screen-pdf-viewer")
        assertSame("the tap must be delivered to the running activity", running, resumedMainActivity())
        Espresso.pressBack()
        awaitTag("screen-course")
        Espresso.pressBack()
        awaitTag("screen-courses")
        composeRule.onNodeWithTag("screen-chats").assertDoesNotExist()
        // The tabs still work after the reset.
        composeRule.onNodeWithTag("nav-chats").performClick()
        awaitTag("screen-chats")
        composeRule.onNodeWithTag("nav-courses").performClick()
        awaitTag("screen-courses")
        finishAndAwaitDestroyed(requireNotNull(running))
        detached = null
    }

    // un-ignored by QMOB-E8-S4 (#64)
    @Test
    fun homeworkDue_tapOpensSheet() {
        seedSignedIn()
        inject(pushData("push-homework-due.json"))

        tap("homework:0e9d8c7b-6a5f-4e3d-9c2b-1a0f9e8d7c6b")

        awaitTag("screen-pdf-viewer")
    }

    // un-ignored by QMOB-E8-S4 (#64)
    @Test
    fun noteSynthesized_ready_tapOpensNoteReader() {
        seedSignedIn()
        inject(pushData("push-note-ready.json"))

        tap("note:$PUSH_NOTE_ID")

        awaitTag("screen-note-reader")
    }

    // un-ignored by QMOB-E8-S4 (#64)
    @Test
    fun deletedFile_tapFallsBackToCourseWithMessage() {
        seedSignedIn()
        missingPaths += "/courses/signals-and-systems/files/$PUSH_FILE_ID"
        inject(pushData("push-files-added.json"))

        tap(FILES_TAG)

        awaitTag("screen-course")
        awaitTag("push-target-missing")
    }

    // --- Preferences: un-ignored by QMOB-E8-S5 (#65) ------------------------------------------------

    // un-ignored by QMOB-E8-S5 (#65)
    @Test
    fun categoryOff_putsCategories_andSuppressesNotification() {
        seedSignedIn()
        launch()
        awaitRequest("PUT", "/push/registration")

        composeRule.onNodeWithTag("action-settings").performClick()
        awaitTag("setting-push-files")
        composeRule.onNodeWithTag("setting-push-files").performScrollTo().performClick()

        // The toggle is mirrored to the server, which then stops sending files.added.
        val put = awaitRequest("PUT", "/push/registration")
        assertEquals(listOf("deadlines", "notes"), JSONObject(put.body.readUtf8()).getJSONArray("categories").strings())
        // A server that is behind still sends one: the app drops it.
        assertEquals(PushHandleResult.Suppressed, inject(pushData("push-files-added.json")))
        assertTrue(pushNotifications().isEmpty())
    }

    // un-ignored by QMOB-E8-S5 (#65)
    @Test
    fun sendTest_postsTestNotification() {
        seedSignedIn()
        launch()

        composeRule.onNodeWithTag("action-settings").performClick()
        awaitTag("action-send-test-notification")
        composeRule.onNodeWithTag("action-send-test-notification").performScrollTo().performClick()

        // POST /push/test answers 200 outcome "sent"; FCM then delivers push.test, injected here.
        awaitRequest("POST", "/push/test")
        awaitTag("push-test-result")
        inject(pushData("push-test.json"))
        val posted = awaitNotification("test")
        assertEquals(PushChannels.SYSTEM, posted.notification.channelId)
    }

    // --- Helpers ------------------------------------------------------------------------------------

    private fun seedSignedIn() {
        runBlocking { sessions.signIn(server.baseUrl, PAIRED_TOKEN, "tester", "Test device") }
        tokenCache.invalidate()
    }

    private fun seedSignedOut() {
        runBlocking { sessions.signOut() }
        tokenCache.invalidate()
    }

    /** Delivers [data] the way FCM does: through a [RemoteMessage] into the app's handler. */
    private fun inject(data: Map<String, String>): PushHandleResult {
        val message = RemoteMessage.Builder("quire@fcm.googleapis.com").setData(data).build()
        return handler.handle(message.data)
    }

    private fun pushNotifications(): List<StatusBarNotification> =
        notificationManager.activeNotifications.filter { it.notification.channelId in PushChannels.ALL }

    private fun awaitNotification(tag: String): StatusBarNotification {
        awaitCondition { pushNotifications().any { it.tag == tag } }
        return pushNotifications().first { it.tag == tag }
    }

    /**
     * Opens the notification tagged [tag] the way a tap does, then waits for `MainActivity` to be resumed (a tap on
     * a closed app cold-starts it; until it is, there is no compose hierarchy to query). System UI sends a real tap
     * with its own background-start privilege; this test is the sender here, so it opts in to the same for the send.
     */
    private fun tap(tag: String) {
        val notification = awaitNotification(tag).notification
        assertNotNull(notification.contentIntent)
        notification.contentIntent.send(context, 0, null, null, null, null, tapOptions())
        awaitCondition { resumedMainActivity() != null }
        composeRule.waitForIdle()
    }

    private fun tapOptions(): Bundle? {
        val mode =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA ->
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                    @Suppress("DEPRECATION") // Replaced by ALLOW_ALWAYS on API 36; still the one API 34-35 know.
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                else -> return null
            }
        return ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(mode).toBundle()
    }

    private fun resumedMainActivity(): MainActivity? {
        var resumed: MainActivity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            resumed =
                ActivityLifecycleMonitorRegistry
                    .getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                    .firstOrNull()
        }
        return resumed
    }

    private fun finishAndAwaitDestroyed(activity: MainActivity) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { activity.finish() }
        awaitCondition {
            var destroyed = false
            instrumentation.runOnMainSync { destroyed = activity.isDestroyed }
            destroyed
        }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        composeRule.waitForIdle()
    }

    /** Pairs through the Pair screen (as the E3 journey does) against the hermetic server. */
    private fun pair() {
        composeRule.onNodeWithTag("field-server-url").performTextReplacement(server.baseUrl)
        composeRule.onNodeWithTag("field-pair-code").performTextReplacement("AbC12345")
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag("action-pair") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("action-pair").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        awaitTag("screen-courses")
    }

    /** Skips recorded requests until one with [method] whose path ends with [suffix] arrives. */
    private fun awaitRequest(
        method: String,
        suffix: String,
    ): RecordedRequest {
        while (true) {
            val request =
                requireNotNull(server.server.takeRequest(TIMEOUT_S, TimeUnit.SECONDS)) { "no $method $suffix" }
            if (request.method == method && request.path?.endsWith(suffix) == true) return request
        }
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "condition not met in ${TIMEOUT_MS}ms" }
            SystemClock.sleep(POLL_MS)
        }
    }

    /**
     * Answers by method + path from `androidTest/assets/fixtures/`, so responses never depend on request order. The
     * push routes mirror ADR 0003 §3.1; the browse routes reuse the E4 fixtures.
     */
    private fun installDispatcher() {
        server.server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path =
                        request.path
                            .orEmpty()
                            .removePrefix(API_PREFIX)
                            .substringBefore('?')
                    if (path in missingPaths) return json("error-not-found.json", 404)
                    return when ("${request.method} $path") {
                        "POST /auth/pair" -> json("pair-success.json")
                        "GET /me" -> json("me.json")
                        "DELETE /auth/token", "DELETE /push/registration" -> MockResponse().setResponseCode(204)
                        "PUT /push/registration" -> json("push-registration.json")
                        "POST /push/test" -> json("push-test-sent.json")
                        "GET /courses" -> json("courses-list.json")
                        "GET /courses/signals-and-systems" -> json("course-detail.json")
                        "GET /courses/signals-and-systems/sessions" -> json("course-sessions.json")
                        "GET /courses/signals-and-systems/files" -> json("course-files.json")
                        "GET /courses/signals-and-systems/notes" -> json("course-notes.json")
                        "GET /courses/signals-and-systems/files/$PUSH_FILE_ID" -> json("push-file.json")
                        "GET /courses/signals-and-systems/notes/$PUSH_NOTE_ID" -> json("reader-note.json")
                        else -> json("error-not-found.json", 404)
                    }
                }
            }
    }

    private fun json(
        name: String,
        code: Int = 200,
    ) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(asset(name))

    private fun pushData(name: String): Map<String, String> {
        val json = JSONObject(asset(name))
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    private fun asset(name: String): String =
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open("fixtures/$name")
            .bufferedReader()
            .use { it.readText() }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private companion object {
        const val API_PREFIX = "/api/mobile/v1"
        const val PAIRED_TOKEN = "qd_live_3f9a8c7e6d5b4a3928171605f4e3d2c1"
        const val FILES_TAG = "files:3f0b1c7e-2a7d-4d8e-9b1a-6c5e4f3a2b10"
        const val PUSH_FILE_ID = "9c4e2b1a-7d3f-4a6b-8e2c-1f0a9b8c7d6e"
        const val PUSH_NOTE_ID = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f"
        const val TIMEOUT_MS = 10_000L
        const val TIMEOUT_S = 10L
        const val POLL_MS = 50L
        const val NOTHING_POSTED_GRACE_MS = 500L
    }
}
