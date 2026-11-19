package com.ezragubbay.quire.core.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.util.Base64

/** Preferences DataStore holding the single encrypted session blob; one instance per process. */
val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Persists the paired session. The whole session (token, server URL, login, device name) is serialized and
 * encrypted with [cipher] before it touches DataStore, so no plain token ever reaches disk. An undecryptable or
 * corrupted store reads as [SessionState.SignedOut] and is wiped rather than crashing.
 *
 * Wiring: the network layer's token provider reads [token]; pairing calls [signIn]; sign-out and a `401` call
 * [signOut].
 */
class SessionRepository(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    scope: CoroutineScope,
) {
    /**
     * In-memory copy of the device token, kept current by the eager [state] collection, [signIn] and [signOut].
     * Private on purpose: it never leaves this class except through [tokenOrNull], and it is never persisted.
     */
    @Volatile
    private var cachedToken: String? = null

    /** Current identity. [SessionState.SignedOut] until the first disk read; use [current] to await it. */
    val state: StateFlow<SessionState> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                val session = decode(prefs)
                cachedToken = session?.token
                session?.toState() ?: SessionState.SignedOut
            }.stateIn(scope, SharingStarted.Eagerly, SessionState.SignedOut)

    /** Reads the persisted session from disk (authoritative; suspends until restored). */
    suspend fun current(): SessionState = read()?.toState() ?: SessionState.SignedOut

    /** The decrypted device token, or null when signed out. */
    suspend fun token(): String? = read()?.token

    /**
     * The warm in-memory token, or null when signed out or before the first disk read lands. Never blocks and
     * never touches DataStore, so it is safe on OkHttp's network threads for unauthenticated calls such as pairing.
     */
    fun tokenOrNull(): String? = cachedToken

    suspend fun signIn(
        serverUrl: String,
        token: String,
        login: String,
        deviceName: String,
    ) {
        val payload = Json.encodeToString(StoredSession(serverUrl, token, login, deviceName)).toByteArray()
        val blob = Base64.getEncoder().encodeToString(cipher.encrypt(payload))
        dataStore.edit { it[BLOB] = blob }
        cachedToken = token
    }

    suspend fun signOut() {
        cachedToken = null
        dataStore.edit { it.remove(BLOB) }
    }

    private suspend fun read(): StoredSession? {
        val prefs = dataStore.data.catch { emit(emptyPreferences()) }.first()
        val session = decode(prefs)
        if (session == null && prefs[BLOB] != null) signOut()
        return session
    }

    @Suppress("SwallowedException")
    private fun decode(prefs: Preferences): StoredSession? {
        val blob = prefs[BLOB] ?: return null
        return try {
            val plain = cipher.decrypt(Base64.getDecoder().decode(blob))
            Json.decodeFromString<StoredSession>(plain.decodeToString())
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            // Bad base64 or malformed JSON (SerializationException is an IllegalArgumentException).
            null
        }
    }

    @Serializable
    private data class StoredSession(
        val serverUrl: String,
        val token: String,
        val login: String,
        val deviceName: String,
    ) {
        fun toState() = SessionState.SignedIn(serverUrl, login, deviceName)

        // Never print the token.
        override fun toString() = "StoredSession(serverUrl=$serverUrl, login=$login, deviceName=$deviceName)"
    }

    private companion object {
        val BLOB = stringPreferencesKey("session_blob")
    }
}
