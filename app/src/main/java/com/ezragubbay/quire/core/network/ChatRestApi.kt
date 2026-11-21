package com.ezragubbay.quire.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Raw Retrofit surface for the non-streaming chat endpoints. Prefer [ChatClient], which maps to [ApiResult]. */
interface ChatRestApi {
    @GET("threads")
    suspend fun recentThreads(
        @Query("limit") limit: Int?,
    ): Response<List<ThreadDto>>

    @GET("courses/{course}/threads")
    suspend fun courseThreads(
        @Path("course") slug: String,
        @Query("documentId") documentId: String?,
    ): Response<List<ThreadDto>>

    @POST("courses/{course}/threads")
    suspend fun createThread(
        @Path("course") slug: String,
        @Body body: CreateThreadRequest,
    ): Response<ThreadDto>

    @DELETE("courses/{course}/threads/{threadId}")
    suspend fun deleteThread(
        @Path("course") slug: String,
        @Path("threadId") threadId: String,
    ): Response<Unit>

    @GET("courses/{course}/threads/{threadId}/messages")
    suspend fun messages(
        @Path("course") slug: String,
        @Path("threadId") threadId: String,
    ): Response<List<ChatMessageDto>>
}
