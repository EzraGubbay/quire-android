package com.ezragubbay.quire.core.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * DataStore and the repository's eager `state` collection run on the runTest scheduler (backgroundScope), never on a
 * real dispatcher: every wait is deterministic and bounded by runTest's timeout, so a lost emission fails fast with a
 * coroutine dump instead of hanging the JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val cipher = FakeCipher()
    private val file: File by lazy { tmp.newFile("session.preferences_pb").also { it.delete() } }

    private fun sessionTest(body: suspend TestScope.() -> Unit) = runTest(timeout = TIMEOUT, testBody = body)

    private fun TestScope.store(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    private fun TestScope.repo(dataStore: DataStore<Preferences> = store()) =
        SessionRepository(dataStore, cipher, backgroundScope)

    private suspend fun SessionRepository.signInDefault() = signIn(SERVER, TOKEN, "ezra", "Pixel 9")

    @Test
    fun freshStore_isSignedOut_withNoToken() =
        sessionTest {
            val repo = repo()
            assertEquals(SessionState.SignedOut, repo.current())
            assertNull(repo.token())
        }

    @Test
    fun signIn_exposesSignedInState_andToken() =
        sessionTest {
            val repo = repo()
            repo.signInDefault()
            assertEquals(SessionState.SignedIn(SERVER, "ezra", "Pixel 9"), repo.current())
            assertEquals(TOKEN, repo.token())
            assertEquals(
                SessionState.SignedIn(SERVER, "ezra", "Pixel 9"),
                repo.state.first { it is SessionState.SignedIn },
            )
        }

    @Test
    fun tokenOrNull_isNullImmediately_whenSignedOut() =
        sessionTest {
            assertNull(repo().tokenOrNull())
        }

    @Test
    fun tokenOrNull_isWarmAfterSignIn_andClearedOnSignOut() =
        sessionTest {
            val repo = repo()
            repo.signInDefault()
            advanceUntilIdle()
            assertEquals(TOKEN, repo.tokenOrNull())

            repo.signOut()
            advanceUntilIdle()
            assertNull(repo.tokenOrNull())
        }

    @Test
    fun tokenOrNull_isRestoredByEagerCollection_withoutBlockingRead() =
        sessionTest {
            val dataStore = store()
            repo(dataStore).signInDefault()
            val restored = repo(dataStore)
            restored.state.first { it is SessionState.SignedIn }
            assertEquals(TOKEN, restored.tokenOrNull())
        }

    @Test
    fun signOut_clearsSessionAndToken() =
        sessionTest {
            val repo = repo()
            repo.signInDefault()
            repo.signOut()
            assertEquals(SessionState.SignedOut, repo.current())
            assertNull(repo.token())
            assertEquals(SessionState.SignedOut, repo.state.first { it is SessionState.SignedOut })
        }

    @Test
    fun restore_survivesNewRepositoryOverSameFile() =
        sessionTest {
            // Simulate a process restart: the first DataStore must be closed before another opens the same file.
            val firstScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
            val first = SessionRepository(store(firstScope), cipher, firstScope)
            first.signInDefault()
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            val restored = repo()
            assertEquals(SessionState.SignedIn(SERVER, "ezra", "Pixel 9"), restored.current())
            assertEquals(TOKEN, restored.token())
        }

    @Test
    fun signIn_overwritesPreviousSession() =
        sessionTest {
            val repo = repo()
            repo.signInDefault()
            repo.signIn("https://other.example", "tok-2", "someone", "Tablet")
            assertEquals(SessionState.SignedIn("https://other.example", "someone", "Tablet"), repo.current())
            assertEquals("tok-2", repo.token())
        }

    @Test
    fun plainToken_isNeverWrittenToDisk() =
        sessionTest {
            val repo = repo()
            repo.signInDefault()
            repo.current()
            val raw = file.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse("token leaked to disk", raw.contains(TOKEN))
            assertTrue("store file should exist", file.length() > 0)
        }

    @Test
    fun undecryptableStore_resolvesToSignedOut_andIsCleared() =
        sessionTest {
            val repo = repo()
            repo.signInDefault()
            cipher.failDecrypt = true
            assertEquals(SessionState.SignedOut, repo.current())
            assertNull(repo.token())
            cipher.failDecrypt = false
            assertEquals(SessionState.SignedOut, repo.current())
        }

    @Test
    fun corruptedPayload_resolvesToSignedOut() =
        sessionTest {
            val dataStore = store()
            dataStore.edit { it[stringPreferencesKey("session_blob")] = "!!!not-base64!!!" }
            assertEquals(SessionState.SignedOut, repo(dataStore).current())
        }

    @Test
    fun decryptableButMalformedJson_resolvesToSignedOut() =
        sessionTest {
            val dataStore = store()
            val blob =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(cipher.encrypt("{nope".toByteArray()))
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
        val TIMEOUT = 30.seconds
    }
}
