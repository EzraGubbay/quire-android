package com.ezragubbay.quire.e2e

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ezragubbay.quire.MainActivity
import com.ezragubbay.quire.testing.MockWebServerRule
import com.ezragubbay.quire.testing.SessionSeedRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Epic e2e journey for QMOB-E3 (auth: device pairing against the mobile API). Defined before any
 * E3 story is implemented (QMOB-E3-S0, #23): every case ships Ignored (reason QMOB-E3) and is
 * un-ignored by the story that satisfies it. The epic is done when no such Ignore remains here.
 * (This KDoc deliberately avoids the literal annotation text so the release-gate grep only matches
 * real annotations, not this note — the lesson from PR #78/#79.)
 *
 * The journey finds UI by **test tag only** (the contract in `docs/e2e/E3-auth.md`), never by
 * display text, and runs against [MockWebServerRule] fixtures whose shapes mirror the real
 * `mobile-v1` pairing contract (`POST /auth/pair`, `GET /me`, `DELETE /auth/token`, error
 * envelope). The user types [MockWebServerRule.baseUrl] into the server-URL field, so the whole
 * journey is hermetic.
 *
 * Journey: fresh install shows the Pair screen (not the tab shell) → enter server URL + code →
 * `POST /auth/pair` 200 stores a token and lands in the shell (Courses) → relaunch stays signed in
 * → an invalid/expired code shows an inline error and stays on Pair → a `quire://pair` deep link
 * pre-fills the form → Sign out from Settings returns to Pair and clears the token → an expired
 * session (401) on relaunch returns to Pair with a message.
 *
 * Case → owning E3 story (the story that removes the Ignore):
 *  - #26 (S3, pairing screen): fresh-install, manual-pair, relaunch-stays-signed-in, invalid-code.
 *  - #27 (S4, QR + deep link): deep-link pre-fill.
 *  - #28 (S5, session lifecycle): sign-out, expired-session relaunch.
 *  - #24 (S1, network core) and #25 (S2, secure session storage) underpin every case but un-ignore
 *    none directly — they are prerequisites, verified by their own unit/instrumented tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AuthJourneyTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    // Every case starts from a fresh install (no stored session).
    @get:Rule(order = 1)
    val session = SessionSeedRule(signedIn = false)

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    @get:Rule(order = 3)
    val server = MockWebServerRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun closeScenario() {
        scenario?.close()
    }

    // --- Pairing screen: un-ignored by QMOB-E3-S3 (#26) -----------------------------------------

    // un-ignored by QMOB-E3-S3 (#26)
    @Test
    fun freshInstall_showsPairScreen_notTabShell() {
        launch()

        composeRule.onNodeWithTag("screen-pair").assertIsDisplayed()
        composeRule.onNodeWithTag("field-server-url").assertIsDisplayed()
        composeRule.onNodeWithTag("field-pair-code").assertIsDisplayed()
        composeRule.onNodeWithTag("field-device-name").assertIsDisplayed()
        composeRule.onNodeWithTag("action-pair").assertIsDisplayed()
        // The tab shell must not be shown before pairing.
        composeRule.onNodeWithTag("app-root").assertDoesNotExist()
    }

    // un-ignored by QMOB-E3-S3 (#26)
    @Test
    fun enterServerUrlAndCode_pairs_landsOnCourses() {
        // POST /auth/pair → 200 mints a token (shape mirrors pairDevice.200.json), then GET /me validates it.
        server.enqueueFixture("pair-success.json")
        server.enqueueFixture("me.json")

        launch()
        pair()

        // Token stored; the back stack is cleared onto the tab shell at Courses.
        composeRule.onNodeWithTag("app-root").assertIsDisplayed()
        composeRule.onNodeWithTag("screen-courses").assertIsDisplayed()

        val request = server.takeRequest()
        assertTrue("Pair must POST the entered code to /auth/pair", request.path!!.endsWith("/auth/pair"))
        assertTrue("Pair must be a POST", request.method == "POST")
    }

    // un-ignored by QMOB-E3-S3 (#26)
    @Test
    fun relaunch_afterPairing_staysSignedIn() {
        server.enqueueFixture("pair-success.json")
        server.enqueueFixture("me.json")
        launch()
        pair()
        composeRule.onNodeWithTag("screen-courses").assertIsDisplayed()

        // On relaunch the app validates the restored session (GET /me → 200).
        server.enqueueFixture("me.json")
        relaunch()
        composeRule.waitForIdle()

        // No re-pairing: the stored token carries the user straight back into the shell.
        composeRule.onNodeWithTag("screen-pair").assertDoesNotExist()
        composeRule.onNodeWithTag("screen-courses").assertIsDisplayed()
    }

    // un-ignored by QMOB-E3-S3 (#26)
    @Test
    fun invalidOrExpiredCode_showsError_staysOnPair() {
        // POST /auth/pair → 400 invalid_request (shape mirrors error.invalid_request.json).
        server.enqueueFixture("error-invalid-code.json", code = 400)

        launch()
        pair()

        // The error renders below the form, which scrolls under the soft keyboard: bring it into view.
        composeRule.onNodeWithTag("pair-error").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("screen-pair").assertIsDisplayed()
        composeRule.onNodeWithTag("app-root").assertDoesNotExist()
    }

    // --- QR + deep link: un-ignored by QMOB-E3-S4 (#27) -----------------------------------------

    // un-ignored by QMOB-E3-S4 (#27)
    @Test
    fun quirePairDeepLink_prefillsServerAndCode() {
        // The web Settings page encodes the server and code as a quire://pair QR / deep link.
        launch(deepLink = "quire://pair?server=https%3A%2F%2Fquire.example.com&code=AbC12345")

        composeRule.onNodeWithTag("screen-pair").assertIsDisplayed()
        composeRule.onNodeWithTag("field-server-url").assertTextContains("https://quire.example.com")
        composeRule.onNodeWithTag("field-pair-code").assertTextContains("AbC12345", ignoreCase = true)
        composeRule.onNodeWithTag("action-scan-qr").assertIsDisplayed()
    }

    // --- Session lifecycle: un-ignored by QMOB-E3-S5 (#28) --------------------------------------

    // un-ignored by QMOB-E3-S5 (#28)
    @Test
    fun signOut_fromSettings_returnsToPair_andClearsToken() {
        server.enqueueFixture("pair-success.json")
        server.enqueueFixture("me.json")
        launch()
        pair()
        composeRule.onNodeWithTag("screen-courses").assertIsDisplayed()

        // Settings → Account → Sign out calls DELETE /auth/token (204, no body).
        composeRule.onNodeWithTag("action-settings").performClick()
        composeRule.onNodeWithTag("settings-account").assertIsDisplayed()
        server.server.enqueue(MockResponse().setResponseCode(204))
        // Settings scrolls and Sign out sits below the fold on the CI emulator: scroll it into view or the
        // click lands off-screen and is a silent no-op.
        composeRule.onNodeWithTag("action-sign-out").performScrollTo().performClick()

        // Sign-out is async (revoke token → clear session → flip state → navigate): wait for Pair.
        composeRule.waitUntil(PAIR_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("screen-pair").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("screen-pair").assertIsDisplayed()

        // Token cleared: a relaunch returns to Pair, not Courses.
        relaunch()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen-pair").assertIsDisplayed()
        composeRule.onNodeWithTag("screen-courses").assertDoesNotExist()
    }

    // un-ignored by QMOB-E3-S5 (#28)
    @Test
    fun expiredSession_onRelaunch_returnsToPairWithMessage() {
        server.enqueueFixture("pair-success.json")
        server.enqueueFixture("me.json")
        launch()
        pair()
        composeRule.onNodeWithTag("screen-courses").assertIsDisplayed()

        // The stored token is revoked server-side: the next authenticated call 401s.
        server.enqueueFixture("error-unauthorized.json", code = 401)
        relaunch()
        composeRule.waitForIdle()

        // A 401 clears the session and routes back to Pair with a "signed out — pair again" message.
        // The 401 round-trip resolves asynchronously (waitForIdle doesn't track the in-flight call),
        // so wait for the routed-back error before asserting.
        composeRule.waitUntil(PAIR_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("pair-error").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("screen-pair").assertIsDisplayed()
        composeRule.onNodeWithTag("pair-error").assertIsDisplayed()
    }

    // --- Helpers --------------------------------------------------------------------------------

    /** Launches [MainActivity], optionally delivering a `quire://pair` [deepLink] as the view intent. */
    private fun launch(deepLink: String? = null) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java)
        if (deepLink != null) {
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse(deepLink)
        }
        scenario = ActivityScenario.launch(intent)
        composeRule.waitForIdle()
    }

    /** Recreates the running activity to model a cold relaunch (persisted session must survive). */
    private fun relaunch() {
        scenario?.recreate()
    }

    /**
     * Fills the Pair form and submits: the server URL is the hermetic [MockWebServerRule.baseUrl],
     * the code mirrors the contract example, and the device name keeps its `Build.MODEL` prefill.
     */
    private fun pair() {
        composeRule.onNodeWithTag("field-server-url").performTextReplacement(server.baseUrl)
        composeRule.onNodeWithTag("field-pair-code").performTextReplacement("AbC12345")
        // The Pair button is disabled until the form validates (canPair). The state that drives it is
        // collected via collectAsStateWithLifecycle, so on a slow emulator it recomposes a beat after the
        // text edits — clicking immediately lands on a still-disabled button, a silent no-op that strands
        // the journey on Pair until waitUntil times out (the API-34 CI flake). Wait for the enabled button,
        // then scroll it into view (the form scrolls under the soft keyboard) before clicking.
        composeRule.waitUntil(PAIR_TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag("action-pair") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("action-pair").performScrollTo().performClick()
        // Pairing is async (pair -> me -> store): wait for it to leave Pair or fail inline.
        composeRule.waitUntil(PAIR_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("screen-pair").fetchSemanticsNodes().isEmpty() ||
                composeRule.onAllNodesWithTag("pair-error").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val PAIR_TIMEOUT_MS = 10_000L
    }
}
