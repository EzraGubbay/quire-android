package com.ezragubbay.quire.core.data.settings

import com.ezragubbay.quire.core.designsystem.theme.ThemeMode

/** The user's persisted appearance preferences. Defaults apply until the user changes something. */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val useDeviceColors: Boolean = false,
)
