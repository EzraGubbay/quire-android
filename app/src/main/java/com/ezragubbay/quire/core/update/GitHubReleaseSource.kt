package com.ezragubbay.quire.core.update

import com.ezragubbay.quire.core.network.QuireJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
internal data class AssetDto(
    val name: String,
)

/**
 * Reads `GET /repos/EzraGubbay/quire-android/releases/latest` unauthenticated. It must be given a client from
 * [httpClient]: never the app's Quire client, whose interceptors would attach the device token.
 */
class GitHubReleaseSource(
    private val client: OkHttpClient,
    private val url: HttpUrl = LATEST_URL.toHttpUrl(),
) : LatestReleaseSource {
    override suspend fun fetch(): FetchResult =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .header("User-Agent", USER_AGENT)
                    .build()
            try {
                client.newCall(request).execute().use(::parse)
            } catch (_: IOException) {
                FetchResult.NetworkError
            }
        }

    private fun parse(response: Response): FetchResult =
        when {
            response.code == HTTP_NOT_FOUND -> FetchResult.NoRelease
            !response.isSuccessful -> FetchResult.HttpError
            else ->
                try {
                    toResult(QuireJson.decodeFromString<GitHubReleaseDto>(response.body?.string().orEmpty()))
                } catch (_: SerializationException) {
                    FetchResult.HttpError
                } catch (_: IllegalArgumentException) {
                    FetchResult.HttpError
                }
        }

    private fun toResult(dto: GitHubReleaseDto): FetchResult =
        if (dto.draft || dto.prerelease || dto.assets.none { it.name.endsWith(".apk", ignoreCase = true) }) {
            FetchResult.NoRelease
        } else {
            val page = dto.htmlUrl.takeIf { it.startsWith("$RELEASES_PAGE/") } ?: "$RELEASES_PAGE/latest"
            FetchResult.Found(LatestRelease(dto.tagName, page))
        }

    companion object {
        const val REPO = "EzraGubbay/quire-android"
        const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
        const val RELEASES_PAGE = "https://github.com/$REPO/releases"
        const val USER_AGENT = "Quire-Android-Update-Check"
        private const val API_VERSION = "2022-11-28"
        private const val HTTP_NOT_FOUND = 404
        private const val CALL_TIMEOUT_SECONDS = 10L
        private const val CACHE_BYTES = 256L * 1024

        /** A client with no interceptors at all, so no header of the app's own server ever reaches GitHub. */
        fun httpClient(cacheDir: File): OkHttpClient =
            OkHttpClient
                .Builder()
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .cache(Cache(cacheDir, CACHE_BYTES))
                .build()
    }
}
