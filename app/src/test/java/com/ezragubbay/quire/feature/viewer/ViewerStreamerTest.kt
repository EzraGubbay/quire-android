package com.ezragubbay.quire.feature.viewer

import com.ezragubbay.quire.core.network.ContentApi
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.IOException

class ViewerStreamerTest {
    @get:Rule val tmp = TemporaryFolder()

    private class FakeApi(
        val response: Response<ResponseBody>,
    ) : ContentApi {
        var calls = 0

        override suspend fun content(
            courseSlug: String,
            fileId: String,
            format: String,
            range: String?,
            ifRange: String?,
        ): Response<ResponseBody> {
            calls++
            return response
        }
    }

    private fun ok(bytes: ByteArray) = Response.success(bytes.toResponseBody("application/pdf".toMediaType()))

    @Test fun streamsToTargetAndReportsProgress() =
        runBlocking {
            val bytes = ByteArray(40_000) { it.toByte() }
            val target = tmp.newFile("x.pdf").also { it.delete() }
            val progress = mutableListOf<Long>()
            ViewerStreamer(FakeApi(ok(bytes)), ViewerCache(tmp.root)).fetch("c", "f", "pdf", target) { read, total ->
                progress += read
                assertEquals(40_000L, total)
            }
            assertArrayEquals(bytes, target.readBytes())
            assertEquals(40_000L, progress.last())
            assertFalse(java.io.File(target.path + ".part").exists())
        }

    @Test fun httpError_throwsAndLeavesNoFile() =
        runBlocking {
            val target = tmp.newFile("y.pdf").also { it.delete() }
            val err = Response.error<ResponseBody>(404, "no".toResponseBody("text/plain".toMediaType()))
            try {
                ViewerStreamer(FakeApi(err), ViewerCache(tmp.root)).fetch("c", "f", "pdf", target) { _, _ -> }
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
}
