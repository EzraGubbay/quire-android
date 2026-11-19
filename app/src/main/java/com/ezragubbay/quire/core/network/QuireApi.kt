package com.ezragubbay.quire.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

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
}
