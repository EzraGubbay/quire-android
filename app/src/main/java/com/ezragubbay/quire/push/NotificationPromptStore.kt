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
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The one-time notification-permission card's state (ADR 0003 §4.4). */
data class NotificationPrompt(
    /** Armed by the first successful pairing and not answered yet: the Courses card shows. */
    val pending: Boolean = false,
    /** The system dialog was launched before; if it will no longer show, Allow opens system settings instead. */
    val requestedBefore: Boolean = false,
)

/** Persists [NotificationPrompt] in the app's settings DataStore. */
@Singleton
class NotificationPromptStore(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.settingsDataStore)

    val prompt: Flow<NotificationPrompt> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { NotificationPrompt(pending = it[PENDING] == true, requestedBefore = it[REQUESTED] == true) }
            .distinctUntilChanged()

    /** Pairing succeeded: show the card once, unless it was already answered on this install. */
    suspend fun armAfterPairing() {
        dataStore.edit { if (it[ASKED] != true) it[PENDING] = true }
    }

    suspend fun markRequested() {
        dataStore.edit { it[REQUESTED] = true }
    }

    /** Allow or Not now: never show the card again. */
    suspend fun markAnswered() {
        dataStore.edit {
            it[ASKED] = true
            it[PENDING] = false
        }
    }

    private companion object {
        val PENDING = booleanPreferencesKey("push_prompt_pending")
        val ASKED = booleanPreferencesKey("push_prompt_asked")
        val REQUESTED = booleanPreferencesKey("push_permission_requested")
    }
}
