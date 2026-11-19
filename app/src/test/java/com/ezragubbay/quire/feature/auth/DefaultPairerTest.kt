package com.ezragubbay.quire.feature.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezragubbay.quire.core.data.session.FakeCipher
import com.ezragubbay.quire.core.data.session.PendingPairing
import com.ezragubbay.quire.core.data.session.SessionRepository
import com.ezragubbay.quire.core.data.session.SessionState
import com.ezragubbay.quire.core.network.DeviceDto
import com.ezragubbay.quire.core.network.MeResponse
import com.ezragubbay.quire.core.network.PairRequest
import com.ezragubbay.quire.core.network.PairResponse
import com.ezragubbay.quire.core.network.QuireApi
import com.ezragubbay.quire.core.network.QuireClient
import com.ezragubbay.quire.core.network.TokenCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

class DefaultPairerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    private class FakeApi(
        var pair: Response<PairResponse> = Response.success(PairResponse("tok-1", DeviceDto("d1", "Pixel 9"))),
        var me: Response<MeResponse> = Response.success(MeResponse("ezra", "v1", DeviceDto("d1", "Pixel 9 (server)"))),
    ) : QuireApi {
        var meTokenSeen: String? = null
        var tokenSource: (() -> String?)? = null

        override suspend fun pair(body: PairRequest) = pair

        override suspend fun me(): Response<MeResponse> {
            meTokenSeen = tokenSource?.invoke()
            return me
        }

        override suspend fun revokeToken(): Response<Unit> = Response.success(Unit)

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

    private fun error(code: Int): Response<Nothing> =
        Response.error(
            code,
            """{"error":{"code":"x","message":"m"}}""".toResponseBody("application/json".toMediaType()),
        )

    private val api = FakeApi()
    private val pending = PendingPairing()
    private val sessions by lazy {
        SessionRepository(
            PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("s.preferences_pb").also { it.delete() } },
            FakeCipher(),
            scope,
        )
    }
    private val tokenCache by lazy { TokenCache { pending.token ?: runBlocking { sessions.token() } } }
    private val pairer by lazy {
        api.tokenSource = { tokenCache.token() }
        DefaultPairer(QuireClient(api), sessions, tokenCache, pending)
    }

    @Test
    fun success_validatesWithMe_usingNewToken_andStoresSession() =
        runBlocking {
            val result = pairer.pair("https://q.example.com", "ABC12345", "Pixel 9")

            assertEquals(PairResult.Success, result)
            assertEquals("tok-1", api.meTokenSeen)
            assertEquals(SessionState.SignedIn("https://q.example.com", "ezra", "Pixel 9 (server)"), sessions.current())
            assertEquals("tok-1", sessions.token())
            // The in-memory override is gone and the cache re-reads the stored token.
            assertNull(pending.token)
            assertNull(pending.serverUrl)
            assertEquals("tok-1", tokenCache.token())
        }

    @Test
    fun codeErrors_mapToSpecificMessages() =
        runBlocking {
            fun env(
                http: Int,
                code: String,
            ) = Response.error<PairResponse>(
                http,
                """{"error":{"code":"$code","message":"m"}}""".toResponseBody("application/json".toMediaType()),
            )
            api.pair = env(400, "invalid_request")
            assertEquals(PairResult.Failure(PairError.InvalidCode), pairer.pair("https://q", "ABC12345", "d"))
            api.pair = env(401, "unauthorized")
            assertEquals(PairResult.Failure(PairError.InvalidCode), pairer.pair("https://q", "ABC12345", "d"))
            api.pair = env(429, "rate_limited")
            assertEquals(PairResult.Failure(PairError.RateLimited), pairer.pair("https://q", "ABC12345", "d"))
            api.pair = env(500, "internal")
            assertEquals(PairResult.Failure(PairError.ServerError), pairer.pair("https://q", "ABC12345", "d"))
            assertNull(sessions.token())
        }

    @Test
    fun networkFailure_isUnreachable() =
        runBlocking {
            val throwing =
                object : QuireApi by api {
                    override suspend fun pair(body: PairRequest): Response<PairResponse> =
                        throw java.io.IOException("down")
                }
            val result =
                DefaultPairer(
                    QuireClient(throwing),
                    sessions,
                    tokenCache,
                    pending,
                ).pair("https://q", "ABC12345", "d")
            assertEquals(PairResult.Failure(PairError.Unreachable), result)
        }

    @Test
    fun meFailure_afterPair_failsWithoutStoringSession() =
        runBlocking {
            api.me =
                Response.error(
                    401,
                    """{"error":{"code":"unauthorized","message":"m"}}""".toResponseBody(
                        "application/json".toMediaType(),
                    ),
                )
            val result = pairer.pair("https://q", "ABC12345", "d")
            assertEquals(PairResult.Failure(PairError.InvalidCode), result)
            assertEquals(SessionState.SignedOut, sessions.current())
            assertNull(pending.token)
        }
}
