package com.ezragubbay.quire.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.designsystem.component.SectionHeader
import com.ezragubbay.quire.push.PushCategory
import com.ezragubbay.quire.push.appNotificationSettings
import com.ezragubbay.quire.push.channelSettingsIntent
import com.ezragubbay.quire.push.permissionDialogWasBlocked
import com.ezragubbay.quire.push.showsNotificationRationale

const val SETTINGS_NOTIFICATIONS_TAG = "settings-notifications"

/**
 * Settings → Notifications (ADR 0003 §4.6): one switch per category with a link to its system channel, the system
 * state when it stands in the way, and "Send test notification". Only controls that can work are shown: no switches
 * while the app's notifications are off in system settings, no test while nothing could be shown.
 */
@Composable
internal fun NotificationsSection(notifications: NotificationActions) {
    val state = notifications.state
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notifications.onResume() }
    // Captured when the request starts, read in the result: survives a configuration change while the dialog is up.
    var requestedBefore by rememberSaveable { mutableStateOf(false) }
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            val blocked =
                permissionDialogWasBlocked(
                    granted = allowed,
                    requestedBefore = requestedBefore,
                    rationaleBefore = rationaleBefore,
                    rationaleAfter = context.showsNotificationRationale(),
                )
            // The system no longer shows its dialog: the app's notification settings are the only way to allow it.
            if (blocked) context.startActivity(appNotificationSettings(context))
            notifications.onPermissionResult()
        }
    // Asks in context (Android 13+ only; below that there is nothing to ask), always through the system first.
    val askPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestedBefore = state.permissionRequestedBefore
            rationaleBefore = context.showsNotificationRationale()
            notifications.onPermissionRequested()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Column(modifier = Modifier.testTag(SETTINGS_NOTIFICATIONS_TAG)) {
        SectionHeader(stringResource(R.string.settings_notifications))
        if (!state.available) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_push_unavailable)) },
                modifier = Modifier.testTag("push-unavailable"),
            )
        } else {
            AvailableContent(state, notifications, askPermission)
        }
    }
}

@Composable
private fun AvailableContent(
    state: NotificationsUiState,
    notifications: NotificationActions,
    askPermission: () -> Unit,
) {
    val context = LocalContext.current
    Column {
        when {
            state.system.appBlocked ->
                SystemStateRow(R.string.settings_push_app_blocked, R.string.settings_push_open_settings) {
                    context.startActivity(appNotificationSettings(context))
                }
            state.system.permissionMissing ->
                SystemStateRow(R.string.settings_push_permission_missing, R.string.settings_push_turn_on, askPermission)
        }
        // Until the stored switches are read, showing the defaults would flash "on" for a category that is off.
        if (state.loaded && !state.system.appBlocked) {
            PushCategory.entries.forEach { category ->
                CategoryRow(
                    category = category,
                    checked = state.preferences.isEnabled(category),
                    blocked = category in state.system.blockedChannels,
                    onCheckedChange = { enabled ->
                        notifications.onCategoryChanged(category, enabled)
                        if (enabled && state.system.permissionMissing) askPermission()
                    },
                    onOpenChannel = { context.startActivity(channelSettingsIntent(context, category)) },
                )
            }
        }
        if (state.notificationsAllowed) TestRows(state, notifications.onSendTest)
    }
}

@Composable
private fun SystemStateRow(
    @StringRes message: Int,
    @StringRes action: Int,
    onAction: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(message)) },
        trailingContent = {
            TextButton(onClick = onAction, modifier = Modifier.testTag("action-push-system-settings")) {
                Text(stringResource(action))
            }
        },
        modifier = Modifier.testTag("push-system-state"),
    )
}

@Composable
private fun CategoryRow(
    category: PushCategory,
    checked: Boolean,
    blocked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenChannel: () -> Unit,
) {
    val label = stringResource(categoryLabel(category))
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                stringResource(if (blocked) R.string.settings_push_channel_blocked else categorySummary(category)),
                color = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenChannel,
                    modifier = Modifier.testTag("action-push-channel-${category.wire}"),
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_push_channel_settings, label),
                    )
                }
                // A blocked channel cannot show anything, whatever the switch says: only the link to unblock it.
                if (!blocked) Switch(checked = checked, onCheckedChange = null)
            }
        },
        modifier =
            Modifier
                .testTag("setting-push-${category.wire}")
                .then(
                    if (blocked) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.settings_push_open_channel_settings),
                            onClick = onOpenChannel,
                        )
                    } else {
                        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                    },
                ),
    )
}

@Composable
private fun TestRows(
    state: NotificationsUiState,
    onSendTest: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_push_send_test)) },
        modifier =
            Modifier
                .testTag("action-send-test-notification")
                .clickable(enabled = !state.sending, role = Role.Button, onClick = onSendTest),
    )
    // Mounted from the first tap on, showing "Sending…" and then the outcome: the live region already exists when
    // its text changes, so TalkBack announces the outcome.
    val text =
        when {
            state.sending -> R.string.settings_push_test_sending
            state.testMessage != null -> testMessageText(state.testMessage)
            else -> return
        }
    ListItem(
        headlineContent = {
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        },
        modifier = Modifier.testTag("push-test-result"),
    )
}

@StringRes
private fun categoryLabel(category: PushCategory): Int =
    when (category) {
        PushCategory.Files -> R.string.push_channel_files
        PushCategory.Deadlines -> R.string.push_channel_deadlines
        PushCategory.Notes -> R.string.push_channel_notes
    }

@StringRes
private fun categorySummary(category: PushCategory): Int =
    when (category) {
        PushCategory.Files -> R.string.settings_push_files_summary
        PushCategory.Deadlines -> R.string.settings_push_deadlines_summary
        PushCategory.Notes -> R.string.settings_push_notes_summary
    }

@StringRes
private fun testMessageText(message: TestPushMessage): Int =
    when (message) {
        TestPushMessage.Sent -> R.string.settings_push_test_sent
        TestPushMessage.Reregistered -> R.string.settings_push_test_reregistered
        TestPushMessage.TryLater -> R.string.settings_push_test_try_later
        TestPushMessage.ServerNotSetUp -> R.string.settings_push_test_server_not_set_up
        TestPushMessage.NotRegistered -> R.string.settings_push_test_not_registered
        TestPushMessage.RateLimited -> R.string.settings_push_test_rate_limited
        TestPushMessage.UnavailableInBuild -> R.string.settings_push_unavailable
        TestPushMessage.Unreachable -> R.string.settings_push_test_unreachable
    }
