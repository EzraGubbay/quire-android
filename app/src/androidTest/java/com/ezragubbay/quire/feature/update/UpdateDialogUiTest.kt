package com.ezragubbay.quire.feature.update

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The update dialog on a real device, in isolation (no activity, no network). */
class UpdateDialogUiTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    @Test
    fun dialogShows_andItsButtonsReport() {
        rule.setContent {
            QuireTheme {
                UpdateDialog(
                    version = "1.2.0",
                    obtainiumInstalled = true,
                    onOpen = { calls += "open" },
                    onLater = { calls += "later" },
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithTag("update-dialog").assertIsDisplayed()
        rule.onNodeWithTag("update-open").performClick()
        rule.onNodeWithTag("update-later").performClick()
        assertEquals(listOf("open", "later"), calls)
    }
}
