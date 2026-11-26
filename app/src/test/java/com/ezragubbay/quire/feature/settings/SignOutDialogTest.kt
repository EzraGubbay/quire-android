package com.ezragubbay.quire.feature.settings

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ezragubbay.quire.core.data.session.SessionState
import com.ezragubbay.quire.core.data.settings.UserSettings
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Settings -> Sign out: immediate with no downloads, else a confirmation offering to remove them. */
@RunWith(RobolectricTestRunner::class)
class SignOutDialogTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<Boolean>()

    private fun show(downloadBytes: Long) {
        rule.setContent {
            QuireTheme {
                SettingsContent(
                    settings = UserSettings(),
                    onBack = {},
                    onThemeModeSelected = {},
                    onUseDeviceColorsChanged = {},
                    account = SessionState.SignedIn("https://q.example.com", "ezra", "Pixel"),
                    onSignOut = { calls += it },
                    downloadBytes = downloadBytes,
                )
            }
        }
    }

    private fun tap(tag: String) {
        // The sign-out row sits below the fold in the settings list; dialog nodes are not scrollable.
        val node = rule.onNodeWithTag(tag)
        if (tag == ACTION_SIGN_OUT_TAG) node.performScrollTo()
        node.performClick()
    }

    @Test
    fun noDownloads_signsOutImmediately_withoutDialog() {
        show(0L)

        tap(ACTION_SIGN_OUT_TAG)

        assertEquals(listOf(false), calls)
        rule.onNodeWithTag(DIALOG_SIGN_OUT_TAG).assertDoesNotExist()
    }

    @Test
    fun withDownloads_asksFirst_showingSize_checkboxUnchecked() {
        show(1536L)

        tap(ACTION_SIGN_OUT_TAG)

        rule.onNodeWithTag(DIALOG_SIGN_OUT_TAG).assertExists()
        rule.onNodeWithText("Also remove downloaded files (1.5 KB)").assertExists()
        rule.onNodeWithTag(SIGN_OUT_REMOVE_DOWNLOADS_TAG).assertIsOff()
        assertEquals(emptyList<Boolean>(), calls)
    }

    @Test
    fun confirmWithoutChecking_keepsDownloads() {
        show(1536L)
        tap(ACTION_SIGN_OUT_TAG)

        tap(ACTION_SIGN_OUT_CONFIRM_TAG)

        assertEquals(listOf(false), calls)
        rule.onNodeWithTag(DIALOG_SIGN_OUT_TAG).assertDoesNotExist()
    }

    @Test
    fun checkThenConfirm_removesDownloads() {
        show(1536L)
        tap(ACTION_SIGN_OUT_TAG)

        tap(SIGN_OUT_REMOVE_DOWNLOADS_TAG)
        rule.onNodeWithTag(SIGN_OUT_REMOVE_DOWNLOADS_TAG).assertIsOn()
        tap(ACTION_SIGN_OUT_CONFIRM_TAG)

        assertEquals(listOf(true), calls)
    }

    @Test
    fun cancel_dismissesWithoutSigningOut() {
        show(1536L)
        tap(ACTION_SIGN_OUT_TAG)

        tap(ACTION_SIGN_OUT_CANCEL_TAG)

        assertEquals(emptyList<Boolean>(), calls)
        rule.onNodeWithTag(DIALOG_SIGN_OUT_TAG).assertDoesNotExist()
    }
}
