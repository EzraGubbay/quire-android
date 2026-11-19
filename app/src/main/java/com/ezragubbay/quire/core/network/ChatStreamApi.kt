package com.ezragubbay.quire.core.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

/**
 * The streaming ask endpoint (`application/x-ndjson`). Separate from [QuireApi] because the body is read
 * incrementally and the call must not be bound by the short browse-call timeout.
 */
interface ChatStreamApi {
    @Streaming
    @POST("courses/{course}/threads/{threadId}/messages")
    suspend fun ask(
        @Path("course") courseSlug: String,
        @Path("threadId") threadId: String,
        @Body body: PostMessageRequest,
    ): Response<ResponseBody>
}
