package com.ezragubbay.quire.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ezragubbay.quire.MainActivity
import com.ezragubbay.quire.testing.MockWebServerRule
import com.ezragubbay.quire.testing.SessionSeedRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Epic e2e journey for QMOB-E2 (app shell). Defined before any shell story is implemented
 * (QMOB-E2-S0, #16): every case ships Ignored (reason QMOB-E2) and is un-ignored by the story that
 * satisfies it. The epic is done when no such Ignore remains here. (This KDoc deliberately avoids
 * the literal annotation text so the release-gate grep only matches real annotations, not this note.)
 *
 * The journey finds UI by **test tag only** (the contract in `docs/e2e/E2-shell.md`), never by
 * display text, and runs against [MockWebServer][okhttp3.mockwebserver.MockWebServer] fixtures.
 *
 * Journey: launch → Courses is the start destination → switch tabs (scroll/state kept) →
 * system back behaviour → open Settings → change theme → relaunch persists the theme.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ShellJourneyTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    // The hermetic server starts first so the seeded session can point at it (Courses fetches from it).
    @get:Rule(order = 1)
    val server = MockWebServerRule(answerSessionCheckOutOfBand = true)

    // The shell is only shown to a paired install.
    @get:Rule(order = 2)
    val session = SessionSeedRule(signedIn = true, serverUrl = { server.baseUrl })

    @get:Rule(order = 3)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedCourses() {
        // Wire the journey to MockWebServer per the harness contract. The shell renders placeholder
        // screens and does not read this yet; the data stories (E4) will consume it. Enqueued here so
        // the journey is hermetic and ready once network lands.
        server.enqueueFixture("courses-list.json")
    }

    // --- Navigation: un-ignored by QMOB-E2-S2 (#18) ---------------------------------------------

    // un-ignored by QMOB-E2-S2 (#18)
    @Test
    fun launch_startsOnCoursesDestination() {
        composeRule.onNodeWithTag("app-root").assertIsDisplayed()
        composeRule.onNodeWithTag("screen-courses").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-courses").assertIsSelected()
    }

    // un-ignored by QMOB-E2-S2 (#18)
    @Test
    fun switchingTabs_showsEachTopLevelDestination() {
        composeRule.onNodeWithTag("nav-downloads").performClick()
        composeRule.onNodeWithTag("screen-downloads").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-downloads").assertIsSelected()

        composeRule.onNodeWithTag("nav-chats").performClick()
        composeRule.onNodeWithTag("screen-chats").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-chats").assertIsSelected()

        composeRule.onNodeWithTag("nav-courses").performClick()
        composeRule.onNodeWithTag("screen-courses").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-courses").assertIsSelected()
    }

    // un-ignored by QMOB-E2-S2 (#18)
    @Test
    fun switchingTabsAndBack_keepsCoursesScrollState() {
        // Scroll a row into view on Courses (once the list has replaced the loading skeleton)...
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("course-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("course-list").performScrollToNode(hasTestTag("course-card-intro-to-ml"))
        composeRule.onNodeWithTag("course-card-intro-to-ml").assertIsDisplayed()

        // ...leave to another tab and come back...
        composeRule.onNodeWithTag("nav-downloads").performClick()
        composeRule.onNodeWithTag("screen-downloads").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-courses").performClick()

        // ...the scrolled row is still shown without re-scrolling (per-tab scroll state kept).
        composeRule.onNodeWithTag("screen-courses").assertIsDisplayed()
        composeRule.onNodeWithTag("course-card-intro-to-ml").assertIsDisplayed()
    }

    // un-ignored by QMOB-E2-S2 (#18)
    @Test
    fun systemBack_fromOtherTab_returnsToCourses() {
        composeRule.onNodeWithTag("nav-chats").performClick()
        composeRule.onNodeWithTag("screen-chats").assertIsDisplayed()

        pressSystemBack()

        composeRule.onNodeWithTag("screen-courses").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-courses").assertIsSelected()
        assertTrue("Returning to Courses must not finish the activity", !composeRule.activity.isFinishing)
    }

    // un-ignored by QMOB-E2-S2 (#18)
    @Test
    fun systemBack_fromCourses_exitsApp() {
        composeRule.onNodeWithTag("screen-courses").assertIsDisplayed()
        // Hold the activity before it finishes: once it is destroyed the rule can no longer hand it out
        // (`composeRule.activity` throws), and how soon that happens after the back press varies run to run.
        val activity = composeRule.activity

        pressSystemBack()

        assertTrue("System back on the start destination must finish the activity", activity.isFinishing)
    }

    // un-ignored by QMOB-E2-S2 (#18)
    @Test
    fun topBarAction_opensSettings() {
        composeRule.onNodeWithTag("action-settings").performClick()
        composeRule.onNodeWithTag("screen-settings").assertIsDisplayed()
    }

    // un-ignored by QMOB-E2-S2 (#18)
    @Test
    fun systemBack_fromSettings_returnsToShell() {
        composeRule.onNodeWithTag("action-settings").performClick()
        composeRule.onNodeWithTag("screen-settings").assertIsDisplayed()

        pressSystemBack()

        composeRule.onNodeWithTag("screen-courses").assertIsDisplayed()
        assertTrue("Leaving Settings must not finish the activity", !composeRule.activity.isFinishing)
    }

    // --- Theme: un-ignored by QMOB-E2-S5 (#21) --------------------------------------------------

    // un-ignored by QMOB-E2-S5 (#21)
    @Test
    fun settings_exposesThemeOptions() {
        composeRule.onNodeWithTag("action-settings").performClick()

        composeRule.onNodeWithTag("setting-theme-system").assertIsDisplayed()
        composeRule.onNodeWithTag("setting-theme-light").assertIsDisplayed()
        composeRule.onNodeWithTag("setting-theme-dark").assertIsDisplayed()
    }

    // un-ignored by QMOB-E2-S5 (#21)
    @Test
    fun changingTheme_selectsOptionImmediately() {
        composeRule.onNodeWithTag("action-settings").performClick()

        composeRule.onNodeWithTag("setting-theme-dark").performClick()
        composeRule.onNodeWithTag("setting-theme-dark").assertIsSelected()
    }

    // un-ignored by QMOB-E2-S5 (#21)
    @Test
    fun themeChoice_persistsAcrossRelaunch() {
        composeRule.onNodeWithTag("action-settings").performClick()
        composeRule.onNodeWithTag("setting-theme-dark").performClick()
        composeRule.onNodeWithTag("setting-theme-dark").assertIsSelected()

        // Relaunch the activity; the persisted preference must survive.
        composeRule.activityRule.scenario.recreate()

        // Navigation restores the back stack onto Settings after recreate(); no need to re-open it.
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setting-theme-dark").assertIsSelected()
    }

    /** Dispatches a system back press on the main thread, then waits for recomposition to settle. */
    private fun pressSystemBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }
}
