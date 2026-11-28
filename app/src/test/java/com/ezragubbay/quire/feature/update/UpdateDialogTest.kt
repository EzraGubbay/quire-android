package com.ezragubbay.quire.feature.update

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateDialogTest {
    @get:Rule val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(installed: Boolean = true) {
        rule.setContent {
            QuireTheme {
                UpdateDialog(
                    version = "1.2.0",
                    obtainiumInstalled = installed,
                    onOpen = { calls += "open" },
                    onLater = { calls += "later" },
                    onDismiss = { calls += "dismiss" },
                )
            }
        }
    }

    @Test
    fun showsTheVersionAndBothButtons() {
        show()
        rule.onNodeWithText("Quire v1.2.0 is available").assertExists()
        rule.onNodeWithText("Open Obtainium").assertExists()
        rule.onNodeWithText("Later").assertExists()
    }

    @Test
    fun open_and_later_eachFireOnce() {
        show()
        rule.onNodeWithTag("update-open").performClick()
        rule.onNodeWithTag("update-later").performClick()
        assertEquals(listOf("open", "later"), calls)
    }

    @Test
    fun withoutObtainium_theButtonReadsViewRelease() {
        show(installed = false)
        rule.onNodeWithText("View release").assertExists()
        rule.onNodeWithText("Open Obtainium").assertDoesNotExist()
    }
}
