package com.ezragubbay.quire.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ezragubbay.quire.core.data.settings.settingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Offline-pinning preferences. [capPausedSlugs] are the pinned courses whose auto-download the cap is holding back. */
data class OfflineSettings(
    val pinned: Set<String> = emptySet(),
    val capBytes: Long = AutoDownloadPolicy.DEFAULT_CAP_BYTES,
    val notify: Boolean = true,
    val capPausedSlugs: Set<String> = emptySet(),
)

/**
 * Persists [OfflineSettings] in the app's settings DataStore, plus the per-course auto-download batches behind the
 * 'N new files downloaded' summary: files are queued by [startBatch] and the summary is due once the last one settles.
 */
class OfflineSettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.settingsDataStore)

    val settings: Flow<OfflineSettings> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                OfflineSettings(
                    pinned = prefs[PINNED].orEmpty(),
                    capBytes = prefs[CAP_BYTES] ?: AutoDownloadPolicy.DEFAULT_CAP_BYTES,
                    notify = prefs[NOTIFY] ?: true,
                    capPausedSlugs = prefs[CAP_PAUSED].orEmpty(),
                )
            }.distinctUntilChanged()

    suspend fun current(): OfflineSettings = settings.first()

    suspend fun addPin(slug: String) {
        dataStore.edit { it[PINNED] = it[PINNED].orEmpty() + slug }
    }

    /** Drops the pin together with its cap-pause flag and any pending batch. */
    suspend fun removePin(slug: String) {
        dataStore.edit { prefs ->
            prefs[PINNED] = prefs[PINNED].orEmpty() - slug
            prefs[CAP_PAUSED] = prefs[CAP_PAUSED].orEmpty() - slug
            prefs[PENDING] = prefs[PENDING].orEmpty().filterNot { it.startsWith(slug + SEP) }.toSet()
            prefs.remove(doneKey(slug))
        }
    }

    suspend fun setCapBytes(bytes: Long) {
        dataStore.edit { it[CAP_BYTES] = bytes }
    }

    suspend fun setNotify(notify: Boolean) {
        dataStore.edit { it[NOTIFY] = notify }
    }

    suspend fun setCapPaused(
        slug: String,
        paused: Boolean,
    ) {
        dataStore.edit {
            it[CAP_PAUSED] =
                it[CAP_PAUSED].orEmpty().let { set ->
                    if (paused) set + slug else set - slug
                }
        }
    }

    /** Adds [fileIds] to the course's pending batch; a batch with nothing pending starts its done-count afresh. */
    suspend fun startBatch(
        slug: String,
        fileIds: Collection<String>,
    ) {
        if (fileIds.isEmpty()) return
        dataStore.edit { prefs ->
            val pending = prefs[PENDING].orEmpty()
            if (pending.none { it.startsWith(slug + SEP) }) prefs.remove(doneKey(slug))
            prefs[PENDING] = pending + fileIds.map { slug + SEP + it }
        }
    }

    /**
     * Settles one file of the course's batch. Returns the number of files that downloaded when this was the last
     * pending one (0 when all failed), else null (batch still open, or the file was not part of one).
     */
    suspend fun settle(
        slug: String,
        fileId: String,
        success: Boolean,
    ): Int? {
        var result: Int? = null
        dataStore.edit { prefs ->
            val pending = prefs[PENDING].orEmpty()
            val entry = slug + SEP + fileId
            if (entry !in pending) return@edit
            val done = (prefs[doneKey(slug)] ?: 0) + if (success) 1 else 0
            val rest = pending - entry
            prefs[PENDING] = rest
            if (rest.none { it.startsWith(slug + SEP) }) {
                prefs.remove(doneKey(slug))
                result = done
            } else {
                prefs[doneKey(slug)] = done
            }
        }
        return result
    }

    private companion object {
        const val SEP = "|"
        val PINNED = stringSetPreferencesKey("offline_pinned")
        val CAP_BYTES = longPreferencesKey("offline_cap_bytes")
        val NOTIFY = booleanPreferencesKey("offline_notify")
        val CAP_PAUSED = stringSetPreferencesKey("offline_cap_paused")
        val PENDING = stringSetPreferencesKey("offline_batch_pending")

        fun doneKey(slug: String) = intPreferencesKey("offline_batch_done_$slug")
    }
}
