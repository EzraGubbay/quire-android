package com.ezragubbay.quire.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/** [StaleTokenFlag] in the app's settings DataStore, so it survives the process (the retry worker may run later). */
class DataStoreStaleTokenFlag(
    private val dataStore: DataStore<Preferences>,
) : StaleTokenFlag {
    override suspend fun isSet(): Boolean = dataStore.data.catch { emit(emptyPreferences()) }.first()[STALE] == true

    override suspend fun set(stale: Boolean) {
        dataStore.edit { if (stale) it[STALE] = true else it.remove(STALE) }
    }

    private companion object {
        val STALE = booleanPreferencesKey("push_token_stale")
    }
}
