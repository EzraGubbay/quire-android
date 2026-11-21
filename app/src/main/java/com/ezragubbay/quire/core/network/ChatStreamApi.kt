package com.ezragubbay.quire.core.network

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

/**
 * The streaming ask endpoint (`application/x-ndjson`). Separate from [QuireApi] because the body is read
 * incrementally and the call must not be bound by the short browse-call timeout.
 *
 * Returns a raw [Call] (not a `suspend` function) so the streamer can [Call.cancel] it to abort an in-flight
 * stream. Cancelling the call closes the socket WITHOUT okio draining the chunked body — which is what
 * `ResponseBody.close()` does, and that drain re-entered okio's AsyncTimeout against the still-blocked reader
 * thread's read on the same source ("Unbalanced enter/exit").
 */
interface ChatStreamApi {
    @Streaming
    @POST("courses/{course}/threads/{threadId}/messages")
    fun ask(
        @Path("course") courseSlug: String,
        @Path("threadId") threadId: String,
        @Body body: PostMessageRequest,
    ): Call<ResponseBody>
}
