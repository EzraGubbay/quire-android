package com.ezragubbay.quire.navigation

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezragubbay.quire.core.data.session.FakeCipher
import com.ezragubbay.quire.core.data.session.SessionRepository
import com.ezragubbay.quire.core.data.session.SessionState
import com.ezragubbay.quire.core.network.DeviceDto
import com.ezragubbay.quire.core.network.MeResponse
import com.ezragubbay.quire.core.network.PairRequest
import com.ezragubbay.quire.core.network.PairResponse
import com.ezragubbay.quire.core.network.QuireApi
import com.ezragubbay.quire.core.network.QuireClient
import com.ezragubbay.quire.core.network.SessionEvents
import com.ezragubbay.quire.core.network.TokenCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        scope.cancel()
    }

    private class FakeApi : QuireApi {
        var me: () -> Response<MeResponse> = { Response.success(MeResponse("ezra", "v1", DeviceDto("d1", "Pixel"))) }
        var revoke: () -> Response<Unit> = { Response.success(Unit) }
        var meCalls = 0
        var revokeCalls = 0

        override suspend fun pair(body: PairRequest): Response<PairResponse> = error("unused")

        override suspend fun me(): Response<MeResponse> {
            meCalls++
            return me.invoke()
        }

        override suspend fun revokeToken(): Response<Unit> {
            revokeCalls++
            return revoke.invoke()
        }

        override suspend fun courses() = error("unused")

        override suspend fun course(slug: String) = error("unused")

        override suspend fun sessions(slug: String) = error("unused")

        override suspend fun files(slug: String) = error("unused")

        override suspend fun notes(slug: String) = error("unused")

        override suspend fun note(
            slug: String,
            noteId: String,
        ) = error("unused")
    }

    private val api = FakeApi()
    private val events = SessionEvents()
    private var cacheReads = 0
    private val tokenCache = TokenCache { "tok".also { cacheReads++ } }
    private val sessions by lazy {
        SessionRepository(
            PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("s.preferences_pb").also { it.delete() } },
            FakeCipher(),
            scope,
        )
    }

    private fun <T> unauthorized(): Response<T> =
        Response.error(
            401,
            """{"error":{"code":"unauthorized","message":"m"}}""".toResponseBody("application/json".toMediaType()),
        )

    private suspend fun signedInSession() = sessions.signIn("https://q.example.com", "tok", "ezra", "Pixel")

    private fun vm() = AppViewModel(sessions, events, tokenCache, QuireClient(api))

    private suspend fun AppViewModel.resolved(): Boolean = signedIn.first { it != null }!!

    @Test
    fun noStoredSession_routesToPair_withoutValidating_orNotice() =
        runBlocking {
            val vm = vm()
            assertFalse(vm.resolved())
            assertEquals(0, api.meCalls)
            assertFalse(vm.sessionExpired.value)
        }

    @Test
    fun storedSession_validatesOnce_andStaysSignedIn() =
        runBlocking {
            signedInSession()
            val vm = vm()
            assertTrue(vm.resolved())
            vm.validation?.join()
            assertEquals(1, api.meCalls)
            assertTrue(vm.signedIn.value == true)
            assertFalse(vm.sessionExpired.value)
        }

    @Test
    fun storedSession_meUnauthorized_clearsSession_andFlagsExpiry() =
        runBlocking {
            signedInSession()
            api.me = { unauthorized() }
            val vm = vm()
            vm.resolved()
            vm.validation?.join()
            vm.signedIn.first { it == false }

            assertTrue(vm.sessionExpired.value)
            assertEquals(SessionState.SignedOut, sessions.current())
            assertNull(sessions.token())
        }

    @Test
    fun storedSession_meOffline_staysSignedIn() =
        runBlocking {
            signedInSession()
            api.me = { throw IOException("offline") }
            val vm = vm()
            vm.resolved()
            vm.validation?.join()

            assertTrue(vm.signedIn.value == true)
            assertFalse(vm.sessionExpired.value)
            assertTrue(sessions.current() is SessionState.SignedIn)
        }

    @Test
    fun expiredEvent_clearsSession_invalidatesCache_andFlagsExpiry() =
        runBlocking {
            signedInSession()
            val vm = vm()
            vm.resolved()
            vm.validation?.join()
            tokenCache.token()
            val readsBefore = cacheReads

            events.emitExpired()
            vm.signedIn.first { it == false }

            assertTrue(vm.sessionExpired.value)
            assertEquals(SessionState.SignedOut, sessions.current())
            tokenCache.token()
            assertEquals(readsBefore + 1, cacheReads)
        }

    @Test
    fun noticeShown_clearsFlag() =
        runBlocking {
            signedInSession()
            api.me = { unauthorized() }
            val vm = vm()
            vm.resolved()
            vm.signedIn.first { it == false }
            vm.onExpiredNoticeShown()
            assertFalse(vm.sessionExpired.value)
        }

    @Test
    fun validateSession_whenSignedIn_revalidatesAfterRecreate() =
        runBlocking {
            signedInSession()
            val vm = vm()
            vm.resolved()
            vm.validation?.join()

            vm.validateSession()
            vm.validation?.join()

            assertEquals(2, api.meCalls)
        }

    @Test
    fun signOut_revokesToken_clearsSession_andRoutesToPair_withoutNotice() =
        runBlocking {
            signedInSession()
            val vm = vm()
            vm.resolved()
            vm.validation?.join()

            vm.signOut().join()

            assertEquals(1, api.revokeCalls)
            assertEquals(false, vm.signedIn.value)
            assertFalse(vm.sessionExpired.value)
            assertNull(sessions.token())
            assertEquals(SessionState.SignedOut, sessions.current())
        }

    @Test
    fun signOut_whenRevokeFails_stillSignsOut() =
        runBlocking {
            signedInSession()
            api.revoke = { throw IOException("offline") }
            val vm = vm()
            vm.resolved()
            vm.validation?.join()

            vm.signOut().join()

            assertEquals(false, vm.signedIn.value)
            assertNull(sessions.token())
        }

    @Test
    fun signOut_whenRevokeIs401_doesNotShowExpiredNotice() =
        runBlocking {
            signedInSession()
            api.revoke = {
                events.emitExpired()
                unauthorized()
            }
            val vm = vm()
            vm.resolved()
            vm.validation?.join()

            vm.signOut().join()

            assertEquals(false, vm.signedIn.value)
            assertFalse(vm.sessionExpired.value)
        }

    @Test
    fun pairedAgain_clearsStaleNotice() =
        runBlocking {
            signedInSession()
            api.me = { unauthorized() }
            val vm = vm()
            vm.resolved()
            vm.signedIn.first { it == false }

            vm.onSignedIn()

            assertTrue(vm.signedIn.value == true)
            assertFalse(vm.sessionExpired.value)
        }
}
