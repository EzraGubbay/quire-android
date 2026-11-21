package com.ezragubbay.quire.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject

/**
 * Turns the streaming ask call into a cold, cancellable `Flow<ChatEvent>`. Each collection issues one HTTP call;
 * cancelling the collector closes the response body, which cancels the connection. Pre-stream HTTP failures and
 * mid-stream I/O failures surface as a terminal [ChatEvent.Error] (never an exception).
 */
class ChatStreamer
    @Inject
    constructor(
        private val api: ChatStreamApi,
    ) {
        fun ask(
            courseSlug: String,
            threadId: String,
            question: String,
        ): Flow<ChatEvent> =
            channelFlow {
                val call = api.ask(courseSlug, threadId, PostMessageRequest(question))
                val response =
                    try {
                        call.execute()
                    } catch (e: IOException) {
                        send(networkError(e))
                        return@channelFlow
                    }
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    send(response.toPreStreamError())
                    return@channelFlow
                }
                launch(Dispatchers.IO) { readBody(body) }
                // On collector cancellation, cancel the CALL (not body.close()): it closes the socket and makes
                // the blocked reader throw, WITHOUT okio draining the chunked body on this thread while the
                // reader still holds the same source's AsyncTimeout (that drain caused "Unbalanced enter/exit").
                awaitClose { call.cancel() }
            }.flowOn(Dispatchers.IO)

        private suspend fun kotlinx.coroutines.channels.ProducerScope<ChatEvent>.readBody(body: ResponseBody) {
            try {
                val terminal = parseChatStream(body.source()) { send(it) }
                if (!terminal) send(ChatEvent.Error(ErrorCode.Internal, ChatEvent.KIND_NETWORK, "Stream ended early"))
            } catch (e: IOException) {
                // Also thrown after a cancel-induced close; send on a closed channel is a no-op via trySend.
                trySend(networkError(e))
            } finally {
                // The reader owns body.close(), so the chunked body's close-time drain runs on the SAME thread
                // as the read, never concurrently (cancellation aborts the socket via call.cancel() above, then
                // this close releases it). On normal completion the read has already finished. Either way okio's
                // AsyncTimeout for this source is only ever entered by one thread.
                body.close()
                close()
            }
        }

        private fun networkError(e: IOException) =
            ChatEvent.Error(ErrorCode.Internal, ChatEvent.KIND_NETWORK, e.message.orEmpty())

        private fun Response<ResponseBody>.toPreStreamError(): ChatEvent.Error {
            val envelope =
                try {
                    errorBody()?.string()?.let { QuireJson.decodeFromString<ErrorEnvelope>(it).error }
                } catch (_: kotlinx.serialization.SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            val code =
                when {
                    envelope != null -> ErrorCode.fromWire(envelope.code)
                    code() == HTTP_UNAUTHORIZED -> ErrorCode.Unauthorized
                    code() == HTTP_NOT_FOUND -> ErrorCode.NotFound
                    code() == HTTP_BAD_REQUEST -> ErrorCode.InvalidRequest
                    code() == HTTP_TOO_MANY -> ErrorCode.RateLimited
                    else -> ErrorCode.Internal
                }
            return ChatEvent.Error(code, null, envelope?.message.orEmpty())
        }

        private companion object {
            const val HTTP_BAD_REQUEST = 400
            const val HTTP_UNAUTHORIZED = 401
            const val HTTP_NOT_FOUND = 404
            const val HTTP_TOO_MANY = 429
        }
    }
