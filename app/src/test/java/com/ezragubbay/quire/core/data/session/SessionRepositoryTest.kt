package com.ezragubbay.quire.core.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val cipher = FakeCipher()
    private val file: File by lazy { tmp.newFile("session.preferences_pb").also { it.delete() } }

    @After
    fun tearDown() = scope.cancel()

    private fun store(): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }

    private fun repo(dataStore: DataStore<Preferences> = store()) = SessionRepository(dataStore, cipher, scope)

    private suspend fun SessionRepository.signInDefault() = signIn(SERVER, TOKEN, "ezra", "Pixel 9")

    @Test
    fun freshStore_isSignedOut_withNoToken() =
        runBlocking {
            val repo = repo()
            assertEquals(SessionState.SignedOut, repo.current())
            assertNull(repo.token())
        }

    @Test
    fun signIn_exposesSignedInState_andToken() =
        runBlocking {
            val repo = repo()
            repo.signInDefault()
            assertEquals(SessionState.SignedIn(SERVER, "ezra", "Pixel 9"), repo.current())
            assertEquals(TOKEN, repo.token())
            assertEquals(SessionState.SignedIn(SERVER, "ezra", "Pixel 9"), repo.state.first { it is SessionState.SignedIn })
        }

    @Test
    fun signOut_clearsSessionAndToken() =
        runBlocking {
            val repo = repo()
            repo.signInDefault()
            repo.signOut()
            assertEquals(SessionState.SignedOut, repo.current())
            assertNull(repo.token())
            assertEquals(SessionState.SignedOut, repo.state.first { it is SessionState.SignedOut })
        }

    @Test
    fun restore_survivesNewRepositoryOverSameFile() =
        runBlocking {
            repo().signInDefault()
            val restored = SessionRepository(store(), cipher, scope)
            assertEquals(SessionState.SignedIn(SERVER, "ezra", "Pixel 9"), restored.current())
            assertEquals(TOKEN, restored.token())
        }

    @Test
    fun signIn_overwritesPreviousSession() =
        runBlocking {
            val repo = repo()
            repo.signInDefault()
            repo.signIn("https://other.example", "tok-2", "someone", "Tablet")
            assertEquals(SessionState.SignedIn("https://other.example", "someone", "Tablet"), repo.current())
            assertEquals("tok-2", repo.token())
        }

    @Test
    fun plainToken_isNeverWrittenToDisk() =
        runBlocking {
            val repo = repo()
            repo.signInDefault()
            repo.current()
            val raw = file.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse("token leaked to disk", raw.contains(TOKEN))
            assertTrue("store file should exist", file.length() > 0)
        }

    @Test
    fun undecryptableStore_resolvesToSignedOut_andIsCleared() =
        runBlocking {
            val dataStore = store()
            val repo = repo(dataStore)
            repo.signInDefault()
            cipher.failDecrypt = true
            assertEquals(SessionState.SignedOut, repo.current())
            assertNull(repo.token())
            cipher.failDecrypt = false
            assertEquals(SessionState.SignedOut, repo.current())
        }

    @Test
    fun corruptedPayload_resolvesToSignedOut() =
        runBlocking {
            val dataStore = store()
            dataStore.edit { it[stringPreferencesKey("session_blob")] = "!!!not-base64!!!" }
            assertEquals(SessionState.SignedOut, repo(dataStore).current())
        }

    @Test
    fun decryptableButMalformedJson_resolvesToSignedOut() =
        runBlocking {
            val dataStore = store()
            val blob = java.util.Base64.getEncoder().encodeToString(cipher.encrypt("{nope".toByteArray()))
            dataStore.edit { it[stringPreferencesKey("session_blob")] = blob }
            assertEquals(SessionState.SignedOut, repo(dataStore).current())
        }

    @Test
    fun signedOutState_hasNoTokenInToString() {
        assertFalse(SessionState.SignedIn(SERVER, "ezra", "Pixel 9").toString().contains(TOKEN))
    }

    private companion object {
        const val SERVER = "https://quire.example.com"
        const val TOKEN = "tok-SECRET-1234567890"
    }
}
