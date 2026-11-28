package com.ezragubbay.quire.core.update

/** The newest published release of the app, as the update prompt needs it. */
data class LatestRelease(
    /** Git tag, for example `v1.2.0`. */
    val tag: String,
    /** A page under this repo's releases; never an arbitrary URL. */
    val htmlUrl: String,
)

sealed interface FetchResult {
    data class Found(
        val release: LatestRelease,
    ) : FetchResult

    /** No installable release: none published, a draft or prerelease, or the APK is not attached yet. */
    data object NoRelease : FetchResult

    /** The server answered but not usefully (rate limit, 5xx, malformed body). */
    data object HttpError : FetchResult

    /** Offline, DNS failure or timeout. */
    data object NetworkError : FetchResult
}

fun interface LatestReleaseSource {
    suspend fun fetch(): FetchResult
}
