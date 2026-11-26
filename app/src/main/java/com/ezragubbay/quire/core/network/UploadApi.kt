package com.ezragubbay.quire.core.network

import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** An upload job as the server reports it (`UploadJob` in the mobile API 1.2.0). */
@Serializable
data class UploadJobDto(
    val uploadId: String,
    /** `queued`, `processing`, `done` or `failed`. */
    val status: String,
    /** `document` or `note`; absent on servers older than 1.2.0. */
    val kind: String? = null,
    /** The created document's id (PDF or Markdown document) once done. */
    val fileId: String? = null,
    /** The created note's id once done. */
    val noteId: String? = null,
    val error: String? = null,
)

/**
 * The upload endpoints. Kept apart from [QuireApi]: the POST streams a raw file body (no overall call timeout) and
 * is idempotent per `Idempotency-Key`, so a retry after a dropped connection never creates a duplicate.
 */
interface UploadApi {
    /** [name] is sent URL-encoded by Retrofit; [kind] is `document` or `note`. The body carries the content type. */
    @POST("courses/{course}/uploads")
    suspend fun create(
        @Path("course") courseSlug: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Query("kind") kind: String,
        @Query("name") name: String,
        @Body body: RequestBody,
    ): Response<UploadJobDto>

    @GET("uploads/{uploadId}")
    suspend fun job(
        @Path("uploadId") uploadId: String,
    ): Response<UploadJobDto>
}
