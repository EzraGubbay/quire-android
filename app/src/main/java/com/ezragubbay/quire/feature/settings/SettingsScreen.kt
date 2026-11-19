package com.ezragubbay.quire.feature.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ezragubbay.quire.BuildConfig
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.data.settings.UserSettings
import com.ezragubbay.quire.core.designsystem.component.QuireTopBar
import com.ezragubbay.quire.core.designsystem.component.SectionHeader
import com.ezragubbay.quire.core.designsystem.theme.ThemeMode

/** Settings destination root: Appearance (theme mode, device colors) and About. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsContent(
        settings = settings,
        onBack = onBack,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onUseDeviceColorsChanged = viewModel::onUseDeviceColorsChanged,
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
