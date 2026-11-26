package com.ezragubbay.quire.feature.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ezragubbay.quire.BuildConfig
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.data.session.SessionState
import com.ezragubbay.quire.core.data.settings.UserSettings
import com.ezragubbay.quire.core.designsystem.component.QuireTopBar
import com.ezragubbay.quire.core.designsystem.component.SectionHeader
import com.ezragubbay.quire.core.designsystem.theme.ThemeMode
import com.ezragubbay.quire.feature.downloads.formatBytes

const val SETTINGS_ACCOUNT_TAG = "settings-account"
const val ACTION_SIGN_OUT_TAG = "action-sign-out"
const val DIALOG_SIGN_OUT_TAG = "dialog-sign-out"
const val SIGN_OUT_REMOVE_DOWNLOADS_TAG = "sign-out-remove-downloads"
const val ACTION_SIGN_OUT_CONFIRM_TAG = "action-sign-out-confirm"
const val ACTION_SIGN_OUT_CANCEL_TAG = "action-sign-out-cancel"

/** Settings destination root: Appearance (theme mode, device colors), Account, Sync, Notifications and About. */
@Composable
fun SettingsScreen(
    account: SessionState.SignedIn? = null,
    onSignOut: (removeDownloads: Boolean) -> Unit = {},
    downloadBytes: Long = 0L,
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    syncViewModel: SyncSettingsViewModel = hiltViewModel(),
    notificationsViewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sync by syncViewModel.state.collectAsStateWithLifecycle()
    val notifications by notificationsViewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        settings = settings,
        onBack = onBack,
        account = account,
        onSignOut = onSignOut,
        downloadBytes = downloadBytes,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onUseDeviceColorsChanged = viewModel::onUseDeviceColorsChanged,
        sync =
            SyncActions(
                state = sync,
                onEnabledChanged = syncViewModel::onEnabledChanged,
                onIntervalSelected = syncViewModel::onIntervalSelected,
                onWifiOnlyChanged = syncViewModel::onWifiOnlyChanged,
                onSyncNow = syncViewModel::onSyncNow,
                offline = OfflineActions(syncViewModel::onNotifyChanged, syncViewModel::onCapSelected),
            ),
        notifications =
            NotificationActions(
                state = notifications,
                onCategoryChanged = notificationsViewModel::onCategoryChanged,
                onResume = notificationsViewModel::onResume,
                onPermissionRequested = notificationsViewModel::onPermissionRequested,
                onPermissionResult = notificationsViewModel::onPermissionResult,
                onSendTest = notificationsViewModel::onSendTest,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: UserSettings,
    onBack: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onUseDeviceColorsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    account: SessionState.SignedIn? = null,
    onSignOut: (removeDownloads: Boolean) -> Unit = {},
    downloadBytes: Long = 0L,
    sync: SyncActions? = null,
    notifications: NotificationActions? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showLicenses by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(
                    "screen-settings",
                ).nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            QuireTopBar(
                title = stringResource(R.string.settings_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.settings_appearance))
            Column(modifier = Modifier.selectableGroup()) {
                ThemeOption(
                    ThemeMode.System,
                    R.string.settings_theme_system,
                    "setting-theme-system",
                    settings,
                    onThemeModeSelected,
                )
                ThemeOption(
                    ThemeMode.Light,
                    R.string.settings_theme_light,
                    "setting-theme-light",
                    settings,
                    onThemeModeSelected,
                )
                ThemeOption(
                    ThemeMode.Dark,
                    R.string.settings_theme_dark,
                    "setting-theme-dark",
                    settings,
                    onThemeModeSelected,
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_device_colors)) },
                    supportingContent = { Text(stringResource(R.string.settings_device_colors_summary)) },
                    trailingContent = { Switch(checked = settings.useDeviceColors, onCheckedChange = null) },
                    modifier =
                        Modifier
                            .testTag("setting-device-colors")
                            .toggleable(
                                value = settings.useDeviceColors,
                                role = Role.Switch,
                                onValueChange = onUseDeviceColorsChanged,
                            ),
                )
            }

            if (account != null) AccountSection(account, downloadBytes, onSignOut)

            if (sync != null) SyncSection(sync)

            if (notifications != null) NotificationsSection(notifications)

            SectionHeader(stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                trailingContent = { Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.testTag("setting-version"),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_licenses)) },
                modifier = Modifier.testTag("setting-licenses").clickable(role = Role.Button) { showLicenses = true },
            )
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = { Text(stringResource(R.string.settings_licenses_body)) },
            confirmButton = {
                TextButton(
                    onClick = { showLicenses = false },
                ) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

/** Who this device is signed in as, plus Sign out. */
@Composable
private fun AccountSection(
    account: SessionState.SignedIn,
    downloadBytes: Long,
    onSignOut: (removeDownloads: Boolean) -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    var removeDownloads by rememberSaveable { mutableStateOf(false) }
    // Nothing downloaded: sign out immediately, as before. Otherwise ask whether to remove the downloads too.
    val onClick = { if (downloadBytes > 0) confirming = true else onSignOut(false) }
    if (confirming) {
        SignOutDialog(
            downloadBytes = downloadBytes,
            removeDownloads = removeDownloads,
            onRemoveDownloadsChange = { removeDownloads = it },
            onConfirm = {
                confirming = false
                onSignOut(removeDownloads)
            },
            onDismiss = { confirming = false },
        )
    }
    Column(modifier = Modifier.testTag(SETTINGS_ACCOUNT_TAG)) {
        SectionHeader(stringResource(R.string.settings_account))
        AccountRow(R.string.settings_account_server, account.serverUrl)
        AccountRow(R.string.settings_account_login, account.login)
        AccountRow(R.string.settings_account_device, account.deviceName)
        ListItem(
            headlineContent = {
                Text(
                    stringResource(R.string.settings_sign_out),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            modifier = Modifier.testTag(ACTION_SIGN_OUT_TAG).clickable(role = Role.Button, onClick = onClick),
        )
    }
}

@Composable
private fun SignOutDialog(
    downloadBytes: Long,
    removeDownloads: Boolean,
    onRemoveDownloadsChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(DIALOG_SIGN_OUT_TAG),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sign_out_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.sign_out_dialog_body))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .toggleable(
                                value = removeDownloads,
                                role = Role.Checkbox,
                                onValueChange = onRemoveDownloadsChange,
                            ).testTag(SIGN_OUT_REMOVE_DOWNLOADS_TAG),
                ) {
                    Checkbox(checked = removeDownloads, onCheckedChange = null)
                    Text(
                        stringResource(R.string.sign_out_remove_downloads, formatBytes(downloadBytes)),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(ACTION_SIGN_OUT_CONFIRM_TAG)) {
                Text(stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(ACTION_SIGN_OUT_CANCEL_TAG)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun AccountRow(
    label: Int,
    value: String,
) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        supportingContent = { Text(value) },
    )
}

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    label: Int,
    tag: String,
    settings: UserSettings,
    onSelected: (ThemeMode) -> Unit,
) {
    val selected = settings.themeMode == mode
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier =
            Modifier
                .testTag(tag)
                .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelected(mode) }),
    )
}
