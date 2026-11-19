package com.ezragubbay.quire.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ezragubbay.quire.core.designsystem.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Preferences DataStore backing [SettingsRepository]; one instance per process. */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Reads and writes [UserSettings]. The theme mode is stored as [ThemeMode.key]. */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<UserSettings> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                UserSettings(
                    themeMode = ThemeMode.fromKey(prefs[THEME_MODE]),
                    useDeviceColors = prefs[USE_DEVICE_COLORS] ?: false,
                )
            }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.key }
    }

    suspend fun setUseDeviceColors(enabled: Boolean) {
        dataStore.edit { it[USE_DEVICE_COLORS] = enabled }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DEVICE_COLORS = booleanPreferencesKey("use_device_colors")
    }
}
