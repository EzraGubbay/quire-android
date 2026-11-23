package com.ezragubbay.quire.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme
import com.ezragubbay.quire.push.NotificationPreferences
import com.ezragubbay.quire.push.PushCategory
import com.ezragubbay.quire.push.SystemNotificationState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Which Settings → Notifications controls show in which state: only controls that can work. */
@RunWith(RobolectricTestRunner::class)
class NotificationsSectionTest {
    @get:Rule val rule = createComposeRule()

    private var state by mutableStateOf(NotificationsUiState(loaded = true))
    private val calls = mutableListOf<String>()

    private fun show(initial: NotificationsUiState) {
        state = initial
        rule.setContent {
            QuireTheme {
                NotificationsSection(
                    NotificationActions(
                        state = state,
                        onCategoryChanged = { category, enabled -> calls += "${category.wire}=$enabled" },
                        onSendTest = { calls += "test" },
                    ),
                )
            }
        }
    }

    private fun exists(tag: String) = rule.onNodeWithTag(tag).assertExists()

    private fun absent(tag: String) = rule.onNodeWithTag(tag).assertDoesNotExist()

    private val categoryTags = PushCategory.entries.map { "setting-push-${it.wire}" }

    @Test
    fun notConfiguredBuild_onlySaysUnavailable() {
        show(NotificationsUiState(loaded = true, available = false))

        exists("settings-notifications")
        exists("push-unavailable")
        categoryTags.forEach(::absent)
        absent("action-send-test-notification")
        absent("push-system-state")
    }

    @Test
    fun allowed_showsEverySwitch_andTheTest_noSystemState() {
        show(
            NotificationsUiState(
                loaded = true,
                preferences = NotificationPreferences().with(PushCategory.Notes, false),
            ),
        )

        rule.onNodeWithTag("setting-push-files").assertIsOn()
        rule.onNodeWithTag("setting-push-deadlines").assertIsOn()
        rule.onNodeWithTag("setting-push-notes").assertIsOff()
        PushCategory.entries.forEach { exists("action-push-channel-${it.wire}") }
        exists("action-send-test-notification")
        absent("push-system-state")
        absent("push-unavailable")
        absent("push-test-result")
    }

    @Test
    fun beforeTheSwitchesAreRead_noSwitchShows() {
        show(NotificationsUiState(loaded = false))

        categoryTags.forEach(::absent)
    }

    @Test
    fun appBlocked_showsTheStateAndItsAction_noSwitches_noTest() {
        show(NotificationsUiState(loaded = true, system = SystemNotificationState(appBlocked = true)))

        exists("push-system-state")
        exists("action-push-system-settings")
        categoryTags.forEach(::absent)
        absent("action-send-test-notification")
    }

    @Test
    fun permissionMissing_keepsTheSwitches_soTurningOneOnCanAsk_butNoTest() {
        show(NotificationsUiState(loaded = true, system = SystemNotificationState(permissionMissing = true)))

        exists("push-system-state")
        categoryTags.forEach(::exists)
        absent("action-send-test-notification")
    }

    @Test
    fun blockedChannel_hasNoSwitch_theOthersDo() {
        show(
            NotificationsUiState(
                loaded = true,
                system = SystemNotificationState(blockedChannels = setOf(PushCategory.Deadlines)),
            ),
        )

        rule
            .onNodeWithTag("setting-push-deadlines")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
        rule.onNodeWithTag("setting-push-files").assertIsOn()
        exists("action-push-channel-deadlines")
    }

    @Test
    fun togglingASwitch_reportsTheCategory() {
        show(NotificationsUiState(loaded = true))

        rule.onNodeWithTag("setting-push-files").performClick()

        assertEquals(listOf("files=false"), calls)
    }

    @Test
    fun testResult_mountsOnTheFirstTap_andTheButtonIsDisabledWhileSending() {
        show(NotificationsUiState(loaded = true))
        rule.onNodeWithTag("action-send-test-notification").performClick()
        assertEquals(listOf("test"), calls)

        state = state.copy(sending = true)
        rule.onNodeWithTag("action-send-test-notification").assertIsNotEnabled()
        exists("push-test-result")

        state = state.copy(sending = false, testMessage = TestPushMessage.Sent)
        rule.onNodeWithTag("action-send-test-notification").assertIsEnabled()
        exists("push-test-result")
    }
}
