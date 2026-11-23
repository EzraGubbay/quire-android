package com.ezragubbay.quire.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ezragubbay.quire.core.data.settings.settingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val SIX = 6L
private const val TWELVE = 12L
private const val TWENTY_FOUR = 24L

/** How often the periodic metadata sync runs. [Off] disables the periodic work (like the master switch). */
enum class SyncInterval(
    val hours: Long,
) {
    Off(0),
    Hours6(SIX),
    Hours12(TWELVE),
    Hours24(TWENTY_FOUR),
    ;

    companion object {
        fun fromHours(hours: Long?): SyncInterval = entries.firstOrNull { it.hours == hours } ?: Hours6
    }
}

/** Background-sync preferences plus the time of the last fully successful sync. */
data class SyncSettings(
    val enabled: Boolean = true,
    val interval: SyncInterval = SyncInterval.Hours6,
    val wifiOnly: Boolean = false,
    val lastSuccessMillis: Long? = null,
) {
    /** True when periodic work should exist. */
    val periodic: Boolean get() = enabled && interval != SyncInterval.Off
}

/** Persists [SyncSettings] in the app's settings DataStore (separate keys from the appearance settings). */
class SyncSettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.settingsDataStore)

    val settings: Flow<SyncSettings> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                SyncSettings(
                    enabled = prefs[ENABLED] ?: true,
                    interval = SyncInterval.fromHours(prefs[INTERVAL_HOURS]?.toLongOrNull()),
                    wifiOnly = prefs[WIFI_ONLY] ?: false,
                    lastSuccessMillis = prefs[LAST_SUCCESS],
                )
            }

    suspend fun current(): SyncSettings = settings.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setInterval(interval: SyncInterval) {
        dataStore.edit { it[INTERVAL_HOURS] = interval.hours.toString() }
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { it[WIFI_ONLY] = wifiOnly }
    }

    suspend fun recordSuccess(atMillis: Long) {
        dataStore.edit { it[LAST_SUCCESS] = atMillis }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("sync_enabled")
        val INTERVAL_HOURS = stringPreferencesKey("sync_interval_hours")
        val WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
        val LAST_SUCCESS = longPreferencesKey("sync_last_success")
    }
}
