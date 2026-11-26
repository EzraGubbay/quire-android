package com.ezragubbay.quire.navigation

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezragubbay.quire.core.data.session.AccountDataCleaner
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
import com.ezragubbay.quire.push.PushSession
import com.ezragubbay.quire.push.PushTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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

    private inner class FakeApi : QuireApi {
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
            order += "revoke"
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

    /** The order the session lifecycle reaches the server and the push hooks in. */
    private val order = mutableListOf<String>()

    private inner class FakePush : PushSession {
        override fun onAppStart() {
            order += "push.appStart"
        }

        override fun onResume() = Unit

        override fun onSignedIn() {
            order += "push.signedIn"
        }

        override suspend fun unregisterRemote() {
            order += "push.unregister"
        }

        override fun onSignedOut() {
            order += "push.signedOut"
        }
    }

    private val push = FakePush()

    private inner class FakeAccountDataCleaner : AccountDataCleaner {
        val bytes = MutableStateFlow(0L)
        val calls = mutableListOf<Boolean>()
        var failWith: Exception? = null

        /** What the session looked like at the moment of the wipe. */
        var sessionAtCall: SessionState? = null
        var signedInAtCall: Boolean? = null
        var probeSignedIn: () -> Boolean? = { null }
        var probeSession = false

        override fun observeDownloadBytes(): Flow<Long> = bytes

        override suspend fun clear(removeDownloads: Boolean) {
            calls += removeDownloads
            sessionAtCall = if (probeSession) sessions.current() else null
            signedInAtCall = probeSignedIn()
            failWith?.let { throw it }
        }
    }

    private val cleaner = FakeAccountDataCleaner()

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

    private fun vm() = AppViewModel(sessions, events, tokenCache, QuireClient(api), push, cleaner)

    /** A repository whose eager state collection and persistence run on [scope], so a test scheduler can drive them. */
    private fun sessionsOn(
        scope: CoroutineScope,
        storeName: String,
    ): SessionRepository {
        val store =
            PreferenceDataStoreFactory.create(scope = scope) {
                tmp.newFile("$storeName.preferences_pb").also { it.delete() }
            }
        return SessionRepository(store, FakeCipher(), scope)
    }

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
    fun storedSession_validatesOnce_andStaysSignedIn() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val testScope = CoroutineScope(dispatcher)
        try {
            runTest(dispatcher) {
                val sessions = sessionsOn(testScope, "validates-once")
                sessions.signIn("https://q.example.com", "tok", "ezra", "Pixel")
                advanceUntilIdle()

                val vm = AppViewModel(sessions, events, tokenCache, QuireClient(api), push, cleaner)
                advanceUntilIdle()

                assertEquals(true, vm.signedIn.value)
                assertEquals(1, api.meCalls)
                assertFalse(vm.sessionExpired.value)
            }
        } finally {
            testScope.cancel()
        }
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
    fun validateSession_whenSignedIn_revalidatesAfterRecreate() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val testScope = CoroutineScope(dispatcher)
        try {
            runTest(dispatcher) {
                val sessions = sessionsOn(testScope, "revalidate")
                sessions.signIn("https://q.example.com", "tok", "ezra", "Pixel")
                advanceUntilIdle()

                val vm = AppViewModel(sessions, events, tokenCache, QuireClient(api), push, cleaner)
                advanceUntilIdle()

                vm.validateSession()
                advanceUntilIdle()

                assertEquals(2, api.meCalls)
            }
        } finally {
            testScope.cancel()
        }
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

    @Test
    fun signOut_deletesPushRegistration_thenRevokes_thenDropsTheLocalToken() =
        runBlocking {
            signedInSession()
            val vm = vm()
            vm.resolved()
            vm.validation?.join()

            vm.signOut().join()

            assertEquals(listOf("push.unregister", "revoke", "push.signedOut"), order)
        }

    @Test
    fun signOut_hungPushDelete_stillLeavesTheRevokeItsBudget() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val testScope = CoroutineScope(dispatcher)
        try {
            runTest(dispatcher) {
                val sessions = sessionsOn(testScope, "hung-push-delete")
                sessions.signIn("https://q.example.com", "tok", "ezra", "Pixel")
                advanceUntilIdle()
                val hung =
                    object : PushSession by push {
                        override suspend fun unregisterRemote() {
                            order += "push.unregister"
                            awaitCancellation()
                        }
                    }
                val vm = AppViewModel(sessions, events, tokenCache, QuireClient(api), hung, cleaner)
                advanceUntilIdle()

                vm.signOut()
                advanceUntilIdle()

                // The push delete is cut off at its own timeout; the revoke still goes out.
                assertEquals(listOf("push.unregister", "revoke", "push.signedOut"), order)
                assertEquals(1, api.revokeCalls)
                assertEquals(false, vm.signedIn.value)
            }
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun pairing_registersForPush() =
        runBlocking {
            val vm = vm()
            vm.resolved()

            vm.onSignedIn()

            assertEquals(listOf("push.signedIn"), order)
        }

    @Test
    fun sessionExpiry_runsOnlyTheLocalPushHalf() =
        runBlocking {
            signedInSession()
            val vm = vm()
            vm.resolved()
            vm.validation?.join()

            events.emitExpired()
            vm.signedIn.first { it == false }

            assertEquals(listOf("push.signedOut"), order)
        }

    // --- Push deep links (QMOB-E8-S4): the pending target ------------------------------------------

    /** Runs [block] with Main and the session store on one [StandardTestDispatcher], so the launch read waits. */
    private fun withPausedLaunch(block: suspend TestScope.(CoroutineScope) -> Unit) {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val testScope = CoroutineScope(dispatcher)
        try {
            runTest(dispatcher) { block(testScope) }
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun pushTarget_whenSignedIn_isPendingUntilConsumed_once() =
        runBlocking {
            signedInSession()
            val vm = vm()
            vm.resolved()
            val target = PushTarget.File("signals-and-systems", "f1")

            vm.openFromPush(target)
            assertEquals(target, vm.pendingPushTarget.value)

            vm.onPushTargetConsumed(target)
            assertNull(vm.pendingPushTarget.value)
            vm.onPushTargetConsumed(target)
            assertNull(vm.pendingPushTarget.value)
        }

    @Test
    fun pushTarget_newerTapReplacesAnUnconsumedOne_andAStaleConsumeKeepsIt() =
        runBlocking {
            signedInSession()
            val vm = vm()
            vm.resolved()
            val first = PushTarget.Course("a")
            val second = PushTarget.Note("b", "n1")

            vm.openFromPush(first)
            vm.openFromPush(second)
            vm.onPushTargetConsumed(first)

            assertEquals(second, vm.pendingPushTarget.value)
        }

    @Test
    fun pushTarget_isHeldWhileTheSessionIsRestored_thenKeptWhenSignedIn() =
        withPausedLaunch { scope ->
            val sessions = sessionsOn(scope, "held-signed-in")
            sessions.signIn("https://q.example.com", "tok", "ezra", "Pixel")
            advanceUntilIdle()
            val vm = AppViewModel(sessions, events, tokenCache, QuireClient(api), push, cleaner)
            val target = PushTarget.Note("signals-and-systems", "n1")

            assertNull(vm.signedIn.value)
            vm.openFromPush(target)
            assertEquals(target, vm.pendingPushTarget.value)

            advanceUntilIdle()
            assertEquals(true, vm.signedIn.value)
            assertEquals(target, vm.pendingPushTarget.value)
        }

    @Test
    fun pushTarget_heldWhileRestoring_isDroppedWhenSignedOut() =
        withPausedLaunch { scope ->
            val sessions = sessionsOn(scope, "held-signed-out")
            val vm = AppViewModel(sessions, events, tokenCache, QuireClient(api), push, cleaner)

            assertNull(vm.signedIn.value)
            vm.openFromPush(PushTarget.Course("signals-and-systems"))

            advanceUntilIdle()
            assertEquals(false, vm.signedIn.value)
            assertNull(vm.pendingPushTarget.value)
        }

    @Test
    fun pushTarget_whileSignedOut_isDropped() =
        runBlocking {
            val vm = vm()
            assertFalse(vm.resolved())

            vm.openFromPush(PushTarget.Course("signals-and-systems"))

            assertNull(vm.pendingPushTarget.value)
        }

    @Test
    fun pushTarget_unconsumed_isDroppedOnSignOut() =
        runBlocking {
            signedInSession()
            val vm = vm()
            vm.resolved()
            vm.validation?.join()
            vm.openFromPush(PushTarget.Course("signals-and-systems"))

            vm.signOut().join()

            assertNull(vm.pendingPushTarget.value)
        }

    @Test
    fun pushTarget_unconsumed_isDroppedOnSessionExpiry() =
        runBlocking {
            signedInSession()
            val vm = vm()
            vm.resolved()
            vm.validation?.join()
            vm.openFromPush(PushTarget.Course("signals-and-systems"))

            events.emitExpired()
            vm.signedIn.first { it == false }

            assertNull(vm.pendingPushTarget.value)
        }

    @Test
    fun signOut_clearsLocalData_afterTheSessionIsCleared_beforeRoutingToPair() =
        runBlocking {
            signedInSession()
            val vm = vm()
            vm.resolved()
            vm.validation?.join()
            cleaner.probeSignedIn = { vm.signedIn.value }
            cleaner.probeSession = true

            vm.signOut().join()

            assertEquals(listOf(false), cleaner.calls)
            assertEquals(SessionState.SignedOut, cleaner.sessionAtCall)
            assertEquals(true, cleaner.signedInAtCall)
            assertEquals(false, vm.signedIn.value)
        }

    @Test
    fun signOut_removeDownloads_passesTheChoiceThrough() =
        runBlocking {
            signedInSession()
            val vm = vm()
            vm.resolved()
            vm.validation?.join()

            vm.signOut(removeDownloads = true).join()

            assertEquals(listOf(true), cleaner.calls)
        }

    @Test
    fun signOut_whenCleanupThrows_stillSignsOut() =
        runBlocking {
            signedInSession()
            val vm = vm()
            vm.resolved()
            vm.validation?.join()
            cleaner.failWith = IllegalStateException("disk full")

            vm.signOut().join()

            assertEquals(false, vm.signedIn.value)
            assertEquals(SessionState.SignedOut, sessions.current())
            assertFalse(vm.sessionExpired.value)
        }

    /** A 401 expiry is not a deliberate sign-out: the user usually re-pairs, so local data stays. */
    @Test
    fun sessionExpiry_doesNotWipeLocalData() =
        runBlocking {
            signedInSession()
            val vm = vm()
            vm.resolved()
            vm.validation?.join()

            events.emitExpired()
            vm.signedIn.first { it == false }

            assertTrue(cleaner.calls.isEmpty())
        }

    @Test
    fun downloadBytes_reflectsTheCleanersFlow() =
        runBlocking {
            val vm = vm()
            vm.resolved()
            cleaner.bytes.value = 2048L

            assertEquals(2048L, vm.downloadBytes.first { it > 0L })
        }
}
