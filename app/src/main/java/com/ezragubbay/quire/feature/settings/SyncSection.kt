package com.ezragubbay.quire.feature.settings

import android.text.format.DateUtils
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.designsystem.component.SectionHeader
import com.ezragubbay.quire.sync.SyncInterval

/** Settings → Sync: background-sync switch, interval, Wi-Fi only, last success and 'Sync now'. */
@Composable
internal fun SyncSection(sync: SyncActions) {
    val settings = sync.state.settings
    var picking by rememberSaveable { mutableStateOf(false) }
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
