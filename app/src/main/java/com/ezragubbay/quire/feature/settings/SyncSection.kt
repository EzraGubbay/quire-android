package com.ezragubbay.quire.feature.settings

import android.Manifest
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.designsystem.component.SectionHeader
import com.ezragubbay.quire.push.NotificationPromptStore
import com.ezragubbay.quire.sync.SyncInterval
import kotlinx.coroutines.launch

/** Settings → Sync: background-sync switch, interval, Wi-Fi only, last success and 'Sync now'. */
@Composable
internal fun SyncSection(sync: SyncActions) {
    val settings = sync.state.settings
    var picking by rememberSaveable { mutableStateOf(false) }
    var pickingCap by rememberSaveable { mutableStateOf(false) }
    val offline = sync.state.offline
    // Turning the summary notification on asks for the notification permission (Android 13+); denial keeps it silent.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    // Shared with the push permission card: once asked here, the card knows a later denial may be a blocked dialog.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val markRequested: () -> Unit = { scope.launch { NotificationPromptStore(context).markRequested() } }
    Column {
        SectionHeader(stringResource(R.string.settings_sync))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_sync_enabled)) },
            supportingContent = { Text(stringResource(R.string.settings_sync_enabled_summary)) },
            trailingContent = { Switch(checked = settings.enabled, onCheckedChange = null) },
            modifier =
                Modifier
                    .testTag("setting-sync-enabled")
                    .toggleable(value = settings.enabled, role = Role.Switch, onValueChange = sync.onEnabledChanged),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_sync_interval)) },
            supportingContent = { Text(intervalLabel(settings.interval)) },
            modifier = Modifier.testTag("setting-sync-interval").clickable(role = Role.Button) { picking = true },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_sync_wifi_only)) },
            trailingContent = { Switch(checked = settings.wifiOnly, onCheckedChange = null) },
            modifier =
                Modifier
                    .testTag("setting-sync-wifi-only")
                    .toggleable(value = settings.wifiOnly, role = Role.Switch, onValueChange = sync.onWifiOnlyChanged),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_sync_last_success)) },
            supportingContent = { Text(lastSuccessLabel(settings.lastSuccessMillis)) },
            modifier = Modifier.testTag("sync-last-success"),
        )
        ListItem(
            headlineContent = {
                Text(
                    stringResource(
                        if (sync.state.syncing) R.string.settings_sync_running else R.string.settings_sync_now,
                    ),
                )
            },
            modifier =
                Modifier
                    .testTag("action-sync-now")
                    .clickable(enabled = !sync.state.syncing, role = Role.Button, onClick = sync.onSyncNow),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_storage_cap)) },
            supportingContent = { Text(capLabel(offline.capBytes)) },
            modifier = Modifier.testTag("setting-storage-cap").clickable(role = Role.Button) { pickingCap = true },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_pin_notify)) },
            supportingContent = { Text(stringResource(R.string.settings_pin_notify_summary)) },
            trailingContent = { Switch(checked = offline.notify, onCheckedChange = null) },
            modifier =
                Modifier
                    .testTag("setting-pin-notify")
                    .toggleable(
                        value = offline.notify,
                        role = Role.Switch,
                        onValueChange = {
                            sync.offline.onNotifyChanged(it)
                            if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                markRequested()
                                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    ),
        )
    }
    if (pickingCap) {
        AlertDialog(
            onDismissRequest = { pickingCap = false },
            title = { Text(stringResource(R.string.settings_storage_cap)) },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    CAP_OPTIONS_GB.forEach { gb ->
                        val bytes = gb * BYTES_PER_GB
                        ListItem(
                            headlineContent = { Text(capLabel(bytes)) },
                            leadingContent = { RadioButton(selected = bytes == offline.capBytes, onClick = null) },
                            modifier =
                                Modifier
                                    .testTag("storage-cap-$gb")
                                    .selectable(
                                        selected = bytes == offline.capBytes,
                                        role = Role.RadioButton,
                                        onClick = {
                                            sync.offline.onCapSelected(bytes)
                                            pickingCap = false
                                        },
                                    ),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingCap = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.settings_sync_interval)) },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    SyncInterval.entries.forEach { option ->
                        ListItem(
                            headlineContent = { Text(intervalLabel(option)) },
                            leadingContent = { RadioButton(selected = option == settings.interval, onClick = null) },
                            modifier =
                                Modifier
                                    .testTag("sync-interval-${option.hours}")
                                    .selectable(
                                        selected = option == settings.interval,
                                        role = Role.RadioButton,
                                        onClick = {
                                            sync.onIntervalSelected(option)
                                            picking = false
                                        },
                                    ),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { picking = false },
                ) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

private val CAP_OPTIONS_GB = listOf(1L, 2L, 5L, 10L)
private const val BYTES_PER_GB = 1024L * 1024 * 1024

@Composable
private fun capLabel(bytes: Long): String = stringResource(R.string.settings_storage_cap_gb, bytes / BYTES_PER_GB)

@Composable
private fun intervalLabel(interval: SyncInterval): String =
    when (interval) {
        SyncInterval.Off -> stringResource(R.string.settings_sync_interval_off)
        SyncInterval.Hours6 -> stringResource(R.string.settings_sync_interval_6h)
        SyncInterval.Hours12 -> stringResource(R.string.settings_sync_interval_12h)
        SyncInterval.Hours24 -> stringResource(R.string.settings_sync_interval_24h)
    }

@Composable
private fun lastSuccessLabel(millis: Long?): String {
    if (millis == null) return stringResource(R.string.settings_sync_never)
    return DateUtils.formatDateTime(
        LocalContext.current,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
}
