package com.ezragubbay.quire.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException

/** Typed entry point to the mobile API: every call returns an [ApiResult] and never throws. */
class QuireClient(
    private val api: QuireApi,
) : BrowseApi {
    suspend fun pair(
        code: String,
        deviceName: String,
    ): ApiResult<PairResponse> = apiCall { api.pair(PairRequest(code, deviceName)) }

    suspend fun me(): ApiResult<MeResponse> = apiCall { api.me() }

    suspend fun revokeToken(): ApiResult<Unit> = apiCall { api.revokeToken() }

    override suspend fun courses(): ApiResult<List<CourseSummaryDto>> = apiCall { api.courses() }

    override suspend fun course(slug: String): ApiResult<CourseDetailDto> = apiCall { api.course(slug) }

    override suspend fun sessions(slug: String): ApiResult<List<CourseSessionDto>> = apiCall { api.sessions(slug) }

    override suspend fun files(slug: String): ApiResult<List<CourseFileDto>> = apiCall { api.files(slug) }

    override suspend fun notes(slug: String): ApiResult<List<NoteDto>> = apiCall { api.notes(slug) }
}

/** Runs [call] and maps the response, error envelope and exceptions into an [ApiResult]. */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> apiCall(call: suspend () -> Response<T>): ApiResult<T> =
    try {
        call().toApiResult()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        ApiResult.Failure.Network(e)
    } catch (e: SerializationException) {
        ApiResult.Failure.Unknown(message = e.message.orEmpty())
    } catch (e: IllegalArgumentException) {
        // kotlinx.serialization raises this for some invalid-shape inputs.
        ApiResult.Failure.Unknown(message = e.message.orEmpty())
    }

private fun <T> Response<T>.toApiResult(): ApiResult<T> {
    if (isSuccessful) {
        @Suppress("UNCHECKED_CAST")
        return ApiResult.Success((body() ?: Unit) as T)
    }
    val envelope =
        try {
            errorBody()?.string()?.let { QuireJson.decodeFromString<ErrorEnvelope>(it).error }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    return mapError(code(), envelope)
}

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY = 429
private const val HTTP_SERVER_ERROR = 500

private fun mapError(
    http: Int,
    envelope: ErrorBody?,
): ApiResult.Failure = if (envelope == null) mapStatus(http) else mapCode(http, envelope.code, envelope.message)

private fun mapCode(
    http: Int,
    code: String,
    message: String,
): ApiResult.Failure =
    when (code) {
        "unauthorized" -> ApiResult.Failure.Unauthorized(message)
        "not_found" -> ApiResult.Failure.NotFound(message)
        "invalid_request" -> ApiResult.Failure.InvalidRequest(message)
        "rate_limited" -> ApiResult.Failure.RateLimited(message)
        "ai_unavailable" -> ApiResult.Failure.AiUnavailable(message)
        "internal" -> ApiResult.Failure.Server(http, message)
        else -> ApiResult.Failure.Unknown(http, message)
    }

private fun mapStatus(http: Int): ApiResult.Failure =
    when {
        http == HTTP_UNAUTHORIZED -> ApiResult.Failure.Unauthorized()
        http == HTTP_NOT_FOUND -> ApiResult.Failure.NotFound()
        http == HTTP_BAD_REQUEST -> ApiResult.Failure.InvalidRequest()
        http == HTTP_TOO_MANY -> ApiResult.Failure.RateLimited()
        http >= HTTP_SERVER_ERROR -> ApiResult.Failure.Server(http)
        else -> ApiResult.Failure.Unknown(http)
    }
