package com.ezragubbay.quire.push

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.ezragubbay.quire.core.data.settings.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The Settings → Notifications category switches (ADR 0003 §4.6). Every category is on until the user turns it off. */
data class NotificationPreferences(
    val enabled: Set<PushCategory> = PushCategory.entries.toSet(),
) {
    fun isEnabled(category: PushCategory): Boolean = category in enabled

    fun with(
        category: PushCategory,
        enabled: Boolean,
    ): NotificationPreferences = copy(enabled = if (enabled) this.enabled + category else this.enabled - category)

    /** What `PUT /push/registration` sends as `categories`, in wire order. */
    val categories: List<PushCategory> get() = PushCategory.entries.filter { it in enabled }
}

/**
 * Persists [NotificationPreferences] in the app's settings DataStore, and is the [PushPreferences] the registrar
 * (which mirrors them to the server) and the message handler (which re-checks them on receipt) read.
 */
@Singleton
class NotificationPreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : PushPreferences {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.settingsDataStore)

    val preferences: Flow<NotificationPreferences> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                NotificationPreferences(PushCategory.entries.filter { prefs[key(it)] != false }.toSet())
            }.distinctUntilChanged()

    override suspend fun isEnabled(category: PushCategory): Boolean = preferences.first().isEnabled(category)

    suspend fun setEnabled(
        category: PushCategory,
        enabled: Boolean,
    ) {
        dataStore.edit { it[key(category)] = enabled }
    }

    private companion object {
        fun key(category: PushCategory) = booleanPreferencesKey("push_category_${category.wire}")
    }
}
