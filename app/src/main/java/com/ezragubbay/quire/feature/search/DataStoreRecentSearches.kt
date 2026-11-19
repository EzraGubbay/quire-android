package com.ezragubbay.quire.feature.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentSearchesStore: DataStore<Preferences> by preferencesDataStore(name = "recent_searches")

private const val SEPARATOR = '\n'

/** [RecentSearches] persisted in a Preferences DataStore (newline-joined, newest first). */
@Singleton
class DataStoreRecentSearches
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : RecentSearches {
        private val store = context.recentSearchesStore
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        override val recents: StateFlow<List<String>> =
            store.data
                .catch { emit(emptyPreferences()) }
                .map { prefs -> prefs[KEY].orEmpty().split(SEPARATOR).filter(String::isNotEmpty) }
                .stateIn(scope, SharingStarted.Eagerly, emptyList())

        override suspend fun add(query: String) {
            val q = query.replace(SEPARATOR, ' ').trim()
            if (q.isEmpty()) return
            store.edit { prefs ->
                val current = prefs[KEY].orEmpty().split(SEPARATOR).filter(String::isNotEmpty)
                prefs[KEY] = (listOf(q) + current.filterNot { it == q }).take(RECENT_LIMIT).joinToString("$SEPARATOR")
            }
        }

        override suspend fun clear() {
            store.edit { it.remove(KEY) }
        }

        private companion object {
            val KEY = stringPreferencesKey("recent")
        }
    }
