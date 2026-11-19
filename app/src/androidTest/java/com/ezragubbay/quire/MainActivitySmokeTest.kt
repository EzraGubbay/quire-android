package com.ezragubbay.quire

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ezragubbay.quire.testing.SessionSeedRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class MainActivitySmokeTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    // The shell is only shown to a paired install.
    @get:Rule(order = 1)
    val session = SessionSeedRule(signedIn = true)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appRootIsDisplayed() {
        composeRule.onNodeWithTag("app-root").assertIsDisplayed()
    }
}
