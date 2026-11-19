package com.ezragubbay.quire.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Raw Retrofit surface for `/api/mobile/v1`. Prefer [QuireClient], which maps results to [ApiResult]. */
interface QuireApi {
    @POST("auth/pair")
    suspend fun pair(
        @Body body: PairRequest,
    ): Response<PairResponse>

    @GET("me")
    suspend fun me(): Response<MeResponse>

    @DELETE("auth/token")
    suspend fun revokeToken(): Response<Unit>

    @GET("courses")
    suspend fun courses(): Response<List<CourseSummaryDto>>

    @GET("courses/{course}")
    suspend fun course(
        @Path("course") slug: String,
    ): Response<CourseDetailDto>

    @GET("courses/{course}/sessions")
    suspend fun sessions(
        @Path("course") slug: String,
    ): Response<List<CourseSessionDto>>

    @GET("courses/{course}/files")
    suspend fun files(
        @Path("course") slug: String,
    ): Response<List<CourseFileDto>>

    @GET("courses/{course}/notes")
    suspend fun notes(
        @Path("course") slug: String,
    ): Response<List<NoteDto>>

    @GET("courses/{course}/notes/{noteId}")
    suspend fun note(
        @Path("course") slug: String,
        @Path("noteId") noteId: String,
    ): Response<NoteDetailDto>
}
