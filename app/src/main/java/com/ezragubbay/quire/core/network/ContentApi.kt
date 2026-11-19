package com.ezragubbay.quire.core.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * The file-content endpoint, which supports HTTP `Range` + `ETag`. Kept apart from [QuireApi] because the body is
 * streamed (never buffered) and the call must not be bound by the short browse-call timeout.
 */
interface ContentApi {
    /** Null [range] / [ifRange] are omitted from the request. */
    @Streaming
    @GET("courses/{course}/files/{fileId}/content")
    suspend fun content(
        @Path("course") courseSlug: String,
        @Path("fileId") fileId: String,
        @Query("format") format: String,
        @Header("Range") range: String?,
        @Header("If-Range") ifRange: String?,
    ): Response<ResponseBody>
}
