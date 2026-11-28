package com.ezragubbay.quire.core.update

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitHubReleaseSourceTest {
    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var source: GitHubReleaseSource

    @Before
    fun setUp() {
        server.start()
        source = GitHubReleaseSource(GitHubReleaseSource.httpClient(tmp.newFolder("cache")), server.url("/latest"))
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun body(
        tag: String = "v1.2.0",
        htmlUrl: String = "https://github.com/EzraGubbay/quire-android/releases/tag/v1.2.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = """[{"name":"quire-android-1.2.0.apk","size":1},{"name":"quire-android-1.2.0.apk.sha256"}]""",
    ) = """{"url":"x","id":1,"tag_name":"$tag","html_url":"$htmlUrl","draft":$draft,"prerelease":$prerelease,
        "name":"v","body":"notes","assets":$assets}"""

    private fun fetch() = runBlocking { source.fetch() }

    @Test
    fun parsesTagAndUrl() {
        server.enqueue(MockResponse().setBody(body()))
        assertEquals(
            FetchResult.Found(
                LatestRelease("v1.2.0", "https://github.com/EzraGubbay/quire-android/releases/tag/v1.2.0"),
            ),
            fetch(),
        )
    }

    @Test
    fun requestCarriesGenericHeadersAndNoAuthorization() {
        server.enqueue(MockResponse().setBody(body()))
        fetch()
        val req = server.takeRequest()
        assertEquals("Quire-Android-Update-Check", req.getHeader("User-Agent"))
        assertEquals("application/vnd.github+json", req.getHeader("Accept"))
        assertEquals("2022-11-28", req.getHeader("X-GitHub-Api-Version"))
        assertNull(req.getHeader("Authorization"))
    }

    @Test
    fun client_hasNoInterceptors() {
        val client = GitHubReleaseSource.httpClient(tmp.newFolder("c2"))
        assertTrue(client.interceptors.isEmpty() && client.networkInterceptors.isEmpty())
    }

    @Test
    fun prereleaseDraftOrNoApk_isNoRelease() {
        server.enqueue(MockResponse().setBody(body(prerelease = true)))
        server.enqueue(MockResponse().setBody(body(draft = true)))
        server.enqueue(MockResponse().setBody(body(assets = """[{"name":"notes.txt"}]""")))
        repeat(3) { assertEquals(FetchResult.NoRelease, fetch()) }
    }

    @Test
    fun statusCodes() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("{not json"))
        assertEquals(FetchResult.NoRelease, fetch())
        assertEquals(FetchResult.HttpError, fetch())
        assertEquals(FetchResult.HttpError, fetch())
        assertEquals(FetchResult.HttpError, fetch())
    }

    @Test
    fun unreachableServer_isNetworkError() {
        server.shutdown()
        assertEquals(FetchResult.NetworkError, fetch())
    }

    @Test
    fun etag_isRevalidatedAndA304ServesTheCachedRelease() {
        server.enqueue(
            MockResponse().setHeader("ETag", "W/\"abc\"").setHeader("Cache-Control", "max-age=0").setBody(body()),
        )
        server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag", "W/\"abc\""))
        val first = fetch()
        val second = fetch()
        server.takeRequest()
        assertEquals("W/\"abc\"", server.takeRequest().getHeader("If-None-Match"))
        assertEquals(first, second)
        assertTrue(second is FetchResult.Found)
    }

    @Test
    fun foreignHtmlUrl_fallsBackToTheReleasesPage() {
        server.enqueue(MockResponse().setBody(body(htmlUrl = "https://evil.example.com/x")))
        val found = fetch() as FetchResult.Found
        assertEquals("https://github.com/EzraGubbay/quire-android/releases/latest", found.release.htmlUrl)
    }
}
