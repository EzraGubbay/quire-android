package com.ezragubbay.quire.feature.viewer

import com.ezragubbay.quire.core.network.FakeServerConfig
import com.ezragubbay.quire.core.network.FakeTokenProvider
import com.ezragubbay.quire.core.network.NetworkOptions
import com.ezragubbay.quire.core.network.QuireClientFactory
import com.ezragubbay.quire.core.network.SessionEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ViewerStreamerTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var cache: ViewerCache

    private val v1 = ByteArray(40_000) { it.toByte() }
    private val v2 = ByteArray(30_000) { (it * 3).toByte() }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        cache = ViewerCache(tmp.root)
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun streamer(revalidateTimeoutMillis: Long = 4_000L) =
        ViewerStreamer(
            QuireClientFactory.contentApi(
                FakeServerConfig(server.url("/").toString()),
                FakeTokenProvider("tok"),
                SessionEvents(),
                NetworkOptions(allowCleartextLocal = true),
            ),
            cache,
            revalidateTimeoutMillis,
        )

    private fun pdf(
        bytes: ByteArray,
        etag: String? = "\"v1\"",
    ) = MockResponse()
        .setHeader("Content-Type", "application/pdf")
        .apply { if (etag != null) setHeader("ETag", etag) }
        .setBody(Buffer().write(bytes))

    private suspend fun ViewerStreamer.openPdf(onProgress: (Long, Long?) -> Unit = { _, _ -> }): File =
        resolve(OpenSource.Stream(FORMAT_PDF), "c", "f1", null, onProgress)

    private fun cached(): File = cache.fileFor("f1", FORMAT_PDF)

    private fun noPartFiles() =
        cache.root
            .listFiles()
            .orEmpty()
            .none { it.name.endsWith(".part") }

    /** Primes the cache with [v1] / `"v1"` through a first, miss open. */
    private suspend fun prime(s: ViewerStreamer) {
        server.enqueue(pdf(v1))
        s.openPdf()
        server.takeRequest()
    }

    @Test fun streamsToTargetAndReportsProgress() =
        runBlocking {
            server.enqueue(pdf(v1))
            val target = tmp.newFile("x.pdf").also { it.delete() }
            val progress = mutableListOf<Long>()
            streamer().fetch("c", "f", "pdf", target) { read, total ->
                progress += read
                assertEquals(40_000L, total)
            }
            assertArrayEquals(v1, target.readBytes())
            assertEquals(40_000L, progress.last())
            assertFalse(File(target.path + ".part").exists())
        }

    @Test fun httpError_throwsAndLeavesNoFile() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(404).setBody("no"))
            val target = tmp.newFile("y.pdf").also { it.delete() }
            try {
                streamer().fetch("c", "f", "pdf", target) { _, _ -> }
                fail("expected IOException")
            } catch (_: IOException) {
                assertFalse(target.exists())
                assertTrue(
                    tmp.root
                        .listFiles()
                        .orEmpty()
                        .none { it.name.endsWith(".part") },
                )
            }
        }

    @Test fun miss_streamsBody_andStoresEtag() =
        runBlocking {
            server.enqueue(pdf(v1, "\"v1\""))
            val file = streamer().openPdf()

            assertArrayEquals(v1, file.readBytes())
            assertEquals("\"v1\"", cache.readEtag(file))
            assertNull(server.takeRequest().getHeader("If-None-Match"))
        }

    @Test fun miss_httpError_stillThrows() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(500))
            try {
                streamer().openPdf()
                fail("a cache miss must still fail on an error")
            } catch (_: IOException) {
                assertFalse(cached().exists())
            }
        }

    @Test fun hit_unchanged_sendsIfNoneMatch_keepsCopy_on304() =
        runBlocking {
            val s = streamer()
            prime(s)
            val file = cached().also { it.setLastModified(1_000L) }
            server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag", "\"v1\""))

            val out = s.openPdf()

            assertEquals(file, out)
            assertArrayEquals(v1, out.readBytes())
            assertEquals("\"v1\"", cache.readEtag(out))
            assertEquals("\"v1\"", server.takeRequest().getHeader("If-None-Match"))
            assertEquals(2, server.requestCount)
            assertTrue("a hit refreshes the LRU mtime", out.lastModified() > 1_000L)
        }

    @Test fun hit_changed_replacesBytesAndEtag_on200() =
        runBlocking {
            val s = streamer()
            prime(s)
            server.enqueue(pdf(v2, "\"v2\""))
            val progress = mutableListOf<Long>()

            val out = s.openPdf { read, _ -> progress += read }

            assertArrayEquals(v2, out.readBytes())
            assertEquals("\"v2\"", cache.readEtag(out))
            assertEquals(v2.size.toLong(), progress.last())
            assertEquals("\"v1\"", server.takeRequest().getHeader("If-None-Match"))
            assertTrue(noPartFiles())
        }

    @Test fun hit_offline_servesCachedCopy() =
        runBlocking {
            val s = streamer()
            prime(s)
            server.shutdown()

            val out = s.openPdf()

            assertArrayEquals(v1, out.readBytes())
            assertEquals("\"v1\"", cache.readEtag(out))
        }

    @Test fun hit_serverError_servesCachedCopy() =
        runBlocking {
            val s = streamer()
            prime(s)
            for (code in listOf(503, 404)) {
                server.enqueue(MockResponse().setResponseCode(code).setBody("nope"))
                val out = s.openPdf()
                assertArrayEquals("status $code", v1, out.readBytes())
                assertEquals("\"v1\"", cache.readEtag(out))
            }
        }

    @Test fun hit_bodyCutMidway_keepsOldCopy() =
        runBlocking {
            val s = streamer()
            prime(s)
            server.enqueue(pdf(v2, "\"v2\"").setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))

            val out = s.openPdf()

            assertArrayEquals(v1, out.readBytes())
            assertEquals("\"v1\"", cache.readEtag(out))
            assertTrue(noPartFiles())
        }

    @Test fun hit_slowServer_servesCachedWithinTimeout() =
        runBlocking {
            val s = streamer(revalidateTimeoutMillis = 200L)
            prime(s)
            server.enqueue(pdf(v2, "\"v2\"").setHeadersDelay(5, TimeUnit.SECONDS))

            val started = System.nanoTime()
            val out = s.openPdf()
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

            assertArrayEquals(v1, out.readBytes())
            assertTrue("served the cached copy promptly, took ${elapsedMs}ms", elapsedMs < 2_000)
        }

    @Test fun hit_withoutSidecar_doesUnconditionalGet() =
        runBlocking {
            val file = cached().also { it.parentFile!!.mkdirs() }
            file.writeBytes(v1) // cached before sidecars existed
            server.enqueue(pdf(v2, "\"v2\""))

            val out = streamer().openPdf()

            assertNull(server.takeRequest().getHeader("If-None-Match"))
            assertArrayEquals(v2, out.readBytes())
            assertEquals("\"v2\"", cache.readEtag(out))
        }

    @Test fun hit_withoutSidecar_offline_servesCachedCopy() =
        runBlocking {
            val file = cached().also { it.parentFile!!.mkdirs() }
            file.writeBytes(v1)
            server.shutdown()

            assertArrayEquals(v1, streamer().openPdf().readBytes())
        }

    @Test fun ok_withoutEtag_clearsSidecar() =
        runBlocking {
            val s = streamer()
            prime(s)
            server.enqueue(pdf(v2, etag = null))
            val out = s.openPdf()
            server.takeRequest()
            assertNull(cache.readEtag(out))
            assertFalse(cache.etagFile(out).exists())

            server.enqueue(pdf(v1, "\"v3\""))
            s.openPdf()
            val next = server.takeRequest()
            assertNull("no validator, so the next open is unconditional", next.getHeader("If-None-Match"))
        }

    @Test fun local_source_makesNoRequest() =
        runBlocking {
            val file = tmp.newFile("downloaded.pdf")
            val out = streamer().resolve(OpenSource.Local(file), "c", "f1", null) { _, _ -> }

            assertEquals(file, out)
            assertEquals(0, server.requestCount)
        }

    @Test fun original_format_revalidatesUnderTitledName() =
        runBlocking {
            val s = streamer()
            val original = OpenSource.Stream("original")
            val open = { runBlocking { s.resolve(original, "c", "f1", "My Deck.pptx") { _, _ -> } } }
            server.enqueue(pdf(v1, "\"o1\""))
            val first = open()
            server.takeRequest()
            assertEquals("f1-My_Deck.pptx", first.name)
            assertTrue(File(first.path + ".etag").exists())

            server.enqueue(MockResponse().setResponseCode(304))
            val second = open()

            assertEquals(first, second)
            assertEquals("\"o1\"", server.takeRequest().getHeader("If-None-Match"))
            assertArrayEquals(v1, second.readBytes())
        }

    @Test fun cancelledDuringRevalidation_propagates_andKeepsCopy() =
        runBlocking {
            val s = streamer(revalidateTimeoutMillis = 30_000L)
            prime(s)
            server.enqueue(pdf(v2, "\"v2\"").setHeadersDelay(10, TimeUnit.SECONDS))

            val job = async(start = CoroutineStart.UNDISPATCHED) { s.openPdf() }
            server.takeRequest(5, TimeUnit.SECONDS)
            delay(100)
            job.cancel()

            try {
                job.await()
                fail("expected cancellation to propagate")
            } catch (_: CancellationException) {
                // expected
            }
            assertArrayEquals(v1, cached().readBytes())
            assertEquals("\"v1\"", cache.readEtag(cached()))
            assertTrue(noPartFiles())
        }
}
