package com.ezragubbay.quire.core.network

/** Outcome of an API call: a decoded value or a typed [Failure] the UI can render. Never throws. */
sealed interface ApiResult<out T> {
    data class Success<out T>(
        val value: T,
    ) : ApiResult<T>

    sealed interface Failure : ApiResult<Nothing> {
        /** Human-readable detail (server message or exception message); may be empty. */
        val message: String

        data class Unauthorized(
            override val message: String = "",
        ) : Failure

        data class NotFound(
            override val message: String = "",
        ) : Failure

        data class InvalidRequest(
            override val message: String = "",
        ) : Failure

        data class RateLimited(
            override val message: String = "",
        ) : Failure

        data class AiUnavailable(
            override val message: String = "",
        ) : Failure

        /** `internal` envelope code or any 5xx without a recognised envelope. */
        data class Server(
            val httpCode: Int,
            override val message: String = "",
        ) : Failure

        /** No usable response: IO error, timeout, no server configured, or a disallowed (cleartext) URL. */
        data class Network(
            val cause: Throwable,
        ) : Failure {
            override val message: String get() = cause.message.orEmpty()
        }

        /** Unrecognised error code/status, or a body that could not be decoded. */
        data class Unknown(
            val httpCode: Int? = null,
            override val message: String = "",
        ) : Failure
    }
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> =
    when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(value))
        is ApiResult.Failure -> this
    }
