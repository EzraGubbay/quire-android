package com.ezragubbay.quire.feature.auth

import com.ezragubbay.quire.core.data.session.PendingPairing
import com.ezragubbay.quire.core.data.session.SessionRepository
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.QuireClient
import com.ezragubbay.quire.core.network.TokenCache
import javax.inject.Inject

/** Why a pairing attempt failed; the screen maps each to a specific inline message. */
enum class PairError { InvalidUrl, InvalidCode, RateLimited, Unreachable, ServerError, NotQuireServer, SessionExpired }

sealed interface PairResult {
    data object Success : PairResult

    data class Failure(
        val error: PairError,
    ) : PairResult
}

/** Exchanges a pairing code for a stored session. */
fun interface Pairer {
    suspend fun pair(
        serverUrl: String,
        code: String,
        deviceName: String,
    ): PairResult
}

/**
 * `POST /auth/pair` -> `GET /me` -> persist the session. The server URL and the new token are held in
 * [PendingPairing] for the duration so the network layer can use them before anything is stored.
 */
class DefaultPairer
    @Inject
    constructor(
        private val client: QuireClient,
        private val sessions: SessionRepository,
        private val tokenCache: TokenCache,
        private val pending: PendingPairing,
    ) : Pairer {
        override suspend fun pair(
            serverUrl: String,
            code: String,
            deviceName: String,
        ): PairResult {
            pending.serverUrl = serverUrl
            pending.token = null
            tokenCache.invalidate()
            try {
                return exchange(serverUrl, code, deviceName)
            } finally {
                // Drop the in-memory override and any cached pending token so the next request reads the
                // persisted session (or none, on failure).
                pending.clear()
                tokenCache.invalidate()
            }
        }

        private suspend fun exchange(
            serverUrl: String,
            code: String,
            deviceName: String,
        ): PairResult {
            val paired = client.pair(code, deviceName)
            if (paired !is ApiResult.Success) return PairResult.Failure((paired as ApiResult.Failure).toPairError())
            val token = paired.value.token
            pending.token = token
            tokenCache.invalidate()
            return when (val me = client.me()) {
                is ApiResult.Failure -> PairResult.Failure(me.toPairError())
                is ApiResult.Success -> {
                    sessions.signIn(serverUrl, token, login = me.value.login, deviceName = me.value.device.name)
                    PairResult.Success
                }
            }
        }
    }

internal fun ApiResult.Failure.toPairError(): PairError =
    when (this) {
        is ApiResult.Failure.Unauthorized, is ApiResult.Failure.InvalidRequest -> PairError.InvalidCode
        is ApiResult.Failure.RateLimited -> PairError.RateLimited
        is ApiResult.Failure.Network -> PairError.Unreachable
        is ApiResult.Failure.Server -> PairError.ServerError
        is ApiResult.Failure.NotFound, is ApiResult.Failure.Unknown, is ApiResult.Failure.AiUnavailable ->
            PairError.NotQuireServer
    }
