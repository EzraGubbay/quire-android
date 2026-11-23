package com.ezragubbay.quire.push

import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.BaseUrlInterceptor
import com.ezragubbay.quire.core.network.CourseFileDto
import com.ezragubbay.quire.core.network.QuireJson
import com.ezragubbay.quire.core.network.apiCall
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** `PUT /push/registration` body (ADR 0003 §3.1). The token never appears in [toString]. */
@Serializable
data class PushRegistrationRequest(
    val fcmToken: String,
    val platform: String,
    val appVersion: String? = null,
    val categories: List<String>,
) {
    override fun toString() =
        "PushRegistrationRequest(platform=$platform, appVersion=$appVersion, categories=$categories)"

    companion object {
        const val PLATFORM_ANDROID = "android"
    }
}

@Serializable
data class PushRegistrationDto(
    val deviceId: String,
    val platform: String,
    val appVersion: String? = null,
    val categories: List<String> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** `PUT` / `GET /push/registration` response. [pushEnabled] says whether this server can send at all. */
@Serializable
data class PushRegistrationResponse(
    val registration: PushRegistrationDto? = null,
    val pushEnabled: Boolean = false,
)

/** `POST /push/test` 200 response; `outcome` is `sent` | `unregistered` | `retryable` | `failed`. */
@Serializable
data class SendTestPushResponse(
    val eventId: String,
    val outcome: String,
)

/** Raw Retrofit surface for the push endpoints (plus the single-file lookup the notification titles use). */
interface PushRestApi {
    @PUT("push/registration")
    suspend fun putRegistration(
        @Body body: PushRegistrationRequest,
    ): Response<PushRegistrationResponse>

    @DELETE("push/registration")
    suspend fun deleteRegistration(): Response<Unit>

    @POST("push/test")
    suspend fun sendTest(): Response<SendTestPushResponse>

    @GET("courses/{course}/files/{fileId}")
    suspend fun file(
        @Path("course") slug: String,
        @Path("fileId") fileId: String,
    ): Response<CourseFileDto>
}

/**
 * Builds [PushRestApi] on [client] (the app's OkHttp stack: base-URL rewrite and bearer token). The placeholder host
 * is replaced per request with the paired server, as for every other mobile API surface.
 */
fun pushRestApi(client: Call.Factory): PushRestApi =
    Retrofit
        .Builder()
        .baseUrl("https://quire.invalid${BaseUrlInterceptor.API_PREFIX}/")
        .callFactory(client)
        .addConverterFactory(QuireJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PushRestApi::class.java)

/** Outcome of `POST /push/test` (ADR 0003 §3.1). */
sealed interface TestPushResult {
    data class Sent(
        val eventId: String,
    ) : TestPushResult

    /** FCM said the token is dead; the server already deleted the registration. The app must re-register. */
    data object Unregistered : TestPushResult

    data object Retryable : TestPushResult

    data object Failed : TestPushResult

    /** `400 invalid_request`: this device has no registration. */
    data object NoRegistration : TestPushResult

    data object RateLimited : TestPushResult

    /** `503 push_unavailable`: push is not set up on the server. */
    data object Unavailable : TestPushResult

    /** Push is not configured in this build. */
    data object NotConfigured : TestPushResult

    data class Error(
        val failure: ApiResult.Failure,
    ) : TestPushResult
}

/** Typed push API: every call returns a result and never throws. */
class PushClient(
    private val api: PushRestApi,
) {
    suspend fun register(body: PushRegistrationRequest): ApiResult<PushRegistrationResponse> =
        apiCall(requireBody = true) { api.putRegistration(body) }

    suspend fun unregister(): ApiResult<Unit> = apiCall { api.deleteRegistration() }

    suspend fun file(
        slug: String,
        fileId: String,
    ): ApiResult<CourseFileDto> = apiCall(requireBody = true) { api.file(slug, fileId) }

    suspend fun sendTest(): TestPushResult =
        when (val result = apiCall(requireBody = true) { api.sendTest() }) {
            is ApiResult.Success ->
                when (result.value.outcome) {
                    OUTCOME_SENT -> TestPushResult.Sent(result.value.eventId)
                    OUTCOME_UNREGISTERED -> TestPushResult.Unregistered
                    OUTCOME_RETRYABLE -> TestPushResult.Retryable
                    else -> TestPushResult.Failed
                }
            is ApiResult.Failure.InvalidRequest -> TestPushResult.NoRegistration
            is ApiResult.Failure.RateLimited -> TestPushResult.RateLimited
            // `push_unavailable` is only returned here, so the shared error mapping reports it as an unknown 503.
            is ApiResult.Failure.Unknown ->
                if (result.httpCode == HTTP_UNAVAILABLE) TestPushResult.Unavailable else TestPushResult.Error(result)
            is ApiResult.Failure.Server ->
                if (result.httpCode == HTTP_UNAVAILABLE) TestPushResult.Unavailable else TestPushResult.Error(result)
            is ApiResult.Failure -> TestPushResult.Error(result)
        }

    private companion object {
        const val OUTCOME_SENT = "sent"
        const val OUTCOME_UNREGISTERED = "unregistered"
        const val OUTCOME_RETRYABLE = "retryable"
        const val HTTP_UNAVAILABLE = 503
    }
}
