package com.ezragubbay.quire.core.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class UpdateState(
    val lastCheckMs: Long? = null,
    val latestTag: String? = null,
    val latestUrl: String? = null,
    val snoozedVersion: String? = null,
)

/** Persists the update-check state in the app's settings DataStore. */
class UpdateStore(
    private val dataStore: DataStore<Preferences>,
) {
    val state: Flow<UpdateState> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map {
                UpdateState(
                    lastCheckMs = it[LAST_CHECK],
                    latestTag = it[LATEST_TAG],
                    latestUrl = it[LATEST_URL],
                    snoozedVersion = it[SNOOZED],
                )
            }.distinctUntilChanged()

    /** A check completed: remember when, and the latest release (or that there is none). */
    suspend fun recordCheck(
        now: Long,
        release: LatestRelease?,
    ) {
        dataStore.edit {
            it[LAST_CHECK] = now
            if (release == null) {
                it.remove(LATEST_TAG)
                it.remove(LATEST_URL)
            } else {
                it[LATEST_TAG] = release.tag
                it[LATEST_URL] = release.htmlUrl
            }
        }
    }

    /** A check failed at the server: back off for the window without touching the cached release. */
    suspend fun recordAttempt(now: Long) {
        dataStore.edit { it[LAST_CHECK] = now }
    }

    suspend fun snooze(version: String) {
        dataStore.edit { it[SNOOZED] = version }
    }

    private companion object {
        val LAST_CHECK = longPreferencesKey("update_last_check_ms")
        val LATEST_TAG = stringPreferencesKey("update_latest_tag")
        val LATEST_URL = stringPreferencesKey("update_latest_url")
        val SNOOZED = stringPreferencesKey("update_snoozed_version")
    }
}
