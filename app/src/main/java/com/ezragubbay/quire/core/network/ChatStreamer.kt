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
                val response =
                    try {
                        api.ask(courseSlug, threadId, PostMessageRequest(question))
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
                // Runs on cancellation of the collector: closing the body unblocks the reader and drops the call.
                awaitClose { body.close() }
            }.flowOn(Dispatchers.IO)

        private suspend fun kotlinx.coroutines.channels.ProducerScope<ChatEvent>.readBody(body: ResponseBody) {
            try {
                val terminal = parseChatStream(body.source()) { send(it) }
                if (!terminal) send(ChatEvent.Error(ErrorCode.Internal, ChatEvent.KIND_NETWORK, "Stream ended early"))
            } catch (e: IOException) {
                // Also thrown after a cancel-induced close; send on a closed channel is a no-op via trySend.
                trySend(networkError(e))
            } finally {
                // Do NOT close the body here. awaitClose owns the single body.close() — on normal
                // completion (after close() below) and on cancel (closing it there is what unblocks the
                // blocking readUtf8Line above). Closing it from this reader coroutine too raced that close
                // on another thread and left okio's process-global AsyncTimeout watchdog unbalanced
                // ("Unbalanced enter/exit"), corrupting every later OkHttp call in the test process.
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
