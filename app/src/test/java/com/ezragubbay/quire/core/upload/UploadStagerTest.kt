package com.ezragubbay.quire.core.upload

import android.net.Uri
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
class UploadStagerTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var paths: UploadPaths
    private val files = mutableMapOf<Uri, Pair<UriMeta, () -> InputStream>>()
    private var ids = 0

    private val source =
        object : UriSource {
            override fun query(uri: Uri) = files.getValue(uri).first

            override fun open(uri: Uri): InputStream = files.getValue(uri).second()
        }

    @Before
    fun setUp() {
        paths = UploadPaths(tmp.root)
    }

    private fun stager() = UploadStager(source, paths) { "id${ids++}" }

    private fun put(
        name: String?,
        bytes: ByteArray,
        mime: String? = null,
        size: Long? = bytes.size.toLong(),
    ): Uri {
        val uri = Uri.parse("content://test/${files.size}")
        files[uri] = UriMeta(name, size, mime) to { ByteArrayInputStream(bytes) }
        return uri
    }

    private val pdf = "%PDF-1.7\n".toByteArray() + ByteArray(100)

    @Test
    fun copiesTheBytesIntoPrivateStorage() {
        val result = stager().stage(put("lecture.pdf", pdf, "application/pdf"), UploadTab.FILES) as StageResult.Staged

        assertEquals("id0", result.id)
        assertEquals("lecture.pdf", result.displayName)
        assertEquals(UploadFormat.PDF, result.format)
        assertEquals(pdf.size.toLong(), result.sizeBytes)
        assertArrayEquals(pdf, result.file.readBytes())
        assertEquals(paths.staged("id0"), result.file)
    }

    @Test
    fun markdownOnNotes_stagesAsNote() {
        val result =
            stager().stage(
                put("w1.md", "# hi".toByteArray(), "text/plain"),
                UploadTab.NOTES,
            ) as StageResult.Staged

        assertEquals(UploadTarget.NOTE, result.target)
        assertEquals(UploadFormat.MARKDOWN, result.format)
    }

    @Test
    fun markdownOverTwoMegabytes_isRejected_andNoPartialFileRemains() {
        val big = ByteArray(2 * 1024 * 1024 + 1) { 'a'.code.toByte() }
        // The provider does not report a size, so the cap has to be enforced while copying.
        val uri = put("big.md", big, size = null)

        val result = stager().stage(uri, UploadTab.NOTES)

        assertEquals(StageResult.Rejected("big.md", UploadRejection.MARKDOWN_TOO_LARGE), result)
        assertFalse(paths.staged("id0").exists())
    }

    @Test
    fun reportedOversizeIsRefusedBeforeCopying() {
        val result = stager().stage(put("big.md", "x".toByteArray(), size = 3L * 1024 * 1024), UploadTab.FILES)

        assertEquals(StageResult.Rejected("big.md", UploadRejection.MARKDOWN_TOO_LARGE), result)
        assertFalse(
            paths.root.exists() &&
                paths.root
                    .listFiles()
                    .orEmpty()
                    .isNotEmpty(),
        )
    }

    @Test
    fun markdownExactlyAtTheLimit_isAccepted() {
        val exact = ByteArray(2 * 1024 * 1024) { 'a'.code.toByte() }

        val result = stager().stage(put("ok.md", exact), UploadTab.FILES)

        assertTrue(result is StageResult.Staged)
    }

    @Test
    fun invalidUtf8Markdown_isRejected_andRemoved() {
        val result = stager().stage(put("bad.md", byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41)), UploadTab.FILES)

        assertEquals(StageResult.Rejected("bad.md", UploadRejection.NOT_UTF8), result)
        assertFalse(paths.staged("id0").exists())
    }

    @Test
    fun emptyFile_isRejected() {
        val result = stager().stage(put("empty.md", ByteArray(0)), UploadTab.FILES)

        assertEquals(StageResult.Rejected("empty.md", UploadRejection.EMPTY), result)
    }

    @Test
    fun unsupportedType_isRejectedWithItsName() {
        val result = stager().stage(put("a.docx", ByteArray(10)), UploadTab.FILES)

        assertEquals(StageResult.Rejected("a.docx", UploadRejection.UNSUPPORTED), result)
    }

    @Test
    fun aProviderThatThrows_isRejectedAsUnreadable_withNothingStaged() {
        val uri = Uri.parse("content://test/boom")
        files[uri] = UriMeta("a.pdf", 10, "application/pdf") to { throw IOException("gone") }

        val result = stager().stage(uri, UploadTab.FILES)

        assertEquals(StageResult.Rejected("a.pdf", UploadRejection.UNREADABLE), result)
        assertFalse(
            paths.root.exists() &&
                paths.root
                    .listFiles()
                    .orEmpty()
                    .isNotEmpty(),
        )
    }
}
