package com.ezragubbay.quire.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.push.NotificationPreferences
import com.ezragubbay.quire.push.NotificationPreferencesStore
import com.ezragubbay.quire.push.NotificationPromptStore
import com.ezragubbay.quire.push.PushCategory
import com.ezragubbay.quire.push.PushControls
import com.ezragubbay.quire.push.SystemNotificationState
import com.ezragubbay.quire.push.SystemNotificationStateReader
import com.ezragubbay.quire.push.TestPushResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What "Send test notification" reports (ADR 0003 §3.1), one message per outcome. */
enum class TestPushMessage {
    /** 200 `outcome: sent`. */
    Sent,

    /**
     * 200 `outcome: unregistered`. By the time the result shows, the registrar has deleted the dead token and
     * registered a fresh one (or handed that to its retry worker), so the next test can be sent.
     */
    Reregistered,

    /** 200 `outcome: retryable` / `failed`, or another server error. */
    TryLater,

    /** 503 `push_unavailable`. */
    ServerNotSetUp,

    /** 400: this device has no registration. */
    NotRegistered,

    /** 429. */
    RateLimited,

    /** Push is not configured in this build. */
    UnavailableInBuild,

    /** No response at all (offline, timeout). */
    Unreachable,
}

fun TestPushResult.toMessage(): TestPushMessage =
    when (this) {
        is TestPushResult.Sent -> TestPushMessage.Sent
        TestPushResult.Unregistered -> TestPushMessage.Reregistered
        TestPushResult.Retryable, TestPushResult.Failed -> TestPushMessage.TryLater
        TestPushResult.Unavailable -> TestPushMessage.ServerNotSetUp
        TestPushResult.NoRegistration -> TestPushMessage.NotRegistered
        TestPushResult.RateLimited -> TestPushMessage.RateLimited
        TestPushResult.NotConfigured -> TestPushMessage.UnavailableInBuild
        is TestPushResult.Error ->
            if (failure is ApiResult.Failure.Network) TestPushMessage.Unreachable else TestPushMessage.TryLater
    }

/** What the Settings → Notifications section renders. */
data class NotificationsUiState(
    /** False until the stored switches have been read. */
    val loaded: Boolean = false,
    /** Push is configured in this build; otherwise the section only says it is unavailable. */
    val available: Boolean = true,
    val preferences: NotificationPreferences = NotificationPreferences(),
    val system: SystemNotificationState = SystemNotificationState(),
    /** The permission dialog was launched before; if it will no longer show, ask via system settings. */
    val permissionRequestedBefore: Boolean = false,
    /** A test push is in flight: the button is disabled. */
    val sending: Boolean = false,
    /** The last test's outcome, if any. */
    val testMessage: TestPushMessage? = null,
) {
    val notificationsAllowed: Boolean get() = system.allowed
}

/** The section's state and callbacks, bundled so [SettingsContent] stays one optional parameter wider. */
class NotificationActions(
    val state: NotificationsUiState,
    val onCategoryChanged: (PushCategory, Boolean) -> Unit = { _, _ -> },
    val onResume: () -> Unit = {},
    val onPermissionRequested: () -> Unit = {},
    val onPermissionResult: () -> Unit = {},
    val onSendTest: () -> Unit = {},
)

private data class TestPushState(
    val sending: Boolean = false,
    val message: TestPushMessage? = null,
)

/**
 * Settings → Notifications (ADR 0003 §4.6). A switch goes to [PushControls.setCategory], which stores it and mirrors
 * the categories to the server (`PUT /push/registration`, debounced) on the application scope. The system
 * state (permission, app and channel switches) is re-read on every resume, since the user changes it elsewhere.
 */
@HiltViewModel
class NotificationSettingsViewModel
    @Inject
    constructor(
        preferences: NotificationPreferencesStore,
        private val push: PushControls,
        private val systemReader: SystemNotificationStateReader,
        private val prompt: NotificationPromptStore,
    ) : ViewModel() {
        private val system = MutableStateFlow(systemReader.read())
        private val test = MutableStateFlow(TestPushState())

        val state: StateFlow<NotificationsUiState> =
            combine(preferences.preferences, system, prompt.prompt, test) { prefs, system, prompt, test ->
                NotificationsUiState(
                    loaded = true,
                    available = push.available,
                    preferences = prefs,
                    system = system,
                    permissionRequestedBefore = prompt.requestedBefore,
                    sending = test.sending,
                    testMessage = test.message,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                NotificationsUiState(available = push.available, system = system.value),
            )

        fun onCategoryChanged(
            category: PushCategory,
            enabled: Boolean,
        ) {
            push.setCategory(category, enabled)
        }

        /** The screen resumed: the user may have changed the permission or a channel in system settings. */
        fun onResume() {
            system.value = systemReader.read()
        }

        fun onPermissionRequested() {
            viewModelScope.launch { prompt.markRequested() }
        }

        /** The permission dialog closed (granted or not): show the new state and register what is now allowed. */
        fun onPermissionResult() {
            onResume()
            push.onCategoriesChanged()
        }

        fun onSendTest() {
            if (test.value.sending) return
            test.value = TestPushState(sending = true)
            viewModelScope.launch {
                val message = push.sendTest().toMessage()
                test.value = TestPushState(message = message)
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
