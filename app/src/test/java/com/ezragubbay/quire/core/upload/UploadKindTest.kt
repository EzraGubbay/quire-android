package com.ezragubbay.quire.core.upload

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadKindTest {
    private val pdfHead = "%PDF-1.7\n".toByteArray()
    private val textHead = "# Notes".toByteArray()

    private fun accepted(
        format: UploadFormat,
        target: UploadTarget,
        savedToDocuments: Boolean = false,
    ) = Classified.Accepted(format, target, savedToDocuments)

    @Test
    fun pdfOnFiles_isAPdfDocument() {
        assertEquals(
            accepted(UploadFormat.PDF, UploadTarget.DOCUMENT),
            UploadKind.classify("lecture.pdf", "application/pdf", pdfHead, UploadTab.FILES),
        )
    }

    @Test
    fun pdfNameWithNonPdfBytes_isRejected() {
        assertEquals(
            Classified.Rejected(UploadRejection.NOT_A_PDF),
            UploadKind.classify("fake.pdf", "application/pdf", textHead, UploadTab.FILES),
        )
    }

    @Test
    fun markdownWithOpaqueMime_isMarkdown_extensionWins() {
        assertEquals(
            accepted(UploadFormat.MARKDOWN, UploadTarget.DOCUMENT),
            UploadKind.classify("week1.md", "application/octet-stream", textHead, UploadTab.FILES),
        )
        assertEquals(
            accepted(UploadFormat.MARKDOWN, UploadTarget.DOCUMENT),
            UploadKind.classify("week1.MD", "text/plain", textHead, UploadTab.FILES),
        )
    }

    @Test
    fun markdownExtensionAndMimeOnly() {
        assertEquals(
            accepted(UploadFormat.MARKDOWN, UploadTarget.DOCUMENT),
            UploadKind.classify("week1.markdown", null, textHead, UploadTab.FILES),
        )
        assertEquals(
            accepted(UploadFormat.MARKDOWN, UploadTarget.DOCUMENT),
            UploadKind.classify("README", "text/markdown; charset=utf-8", textHead, UploadTab.FILES),
        )
    }

    @Test
    fun markdownOnNotes_isANote() {
        assertEquals(
            accepted(UploadFormat.MARKDOWN, UploadTarget.NOTE),
            UploadKind.classify("week1.md", "text/markdown", textHead, UploadTab.NOTES),
        )
    }

    @Test
    fun pdfOnNotes_isUploadedAsADocument_andFlaggedSoTheUserIsTold() {
        assertEquals(
            accepted(UploadFormat.PDF, UploadTarget.DOCUMENT, savedToDocuments = true),
            UploadKind.classify("paper.pdf", "application/pdf", pdfHead, UploadTab.NOTES),
        )
    }

    @Test
    fun pdfWithNoExtensionAndOpaqueMime_isDetectedByItsMagicBytes() {
        assertEquals(
            accepted(UploadFormat.PDF, UploadTarget.DOCUMENT),
            UploadKind.classify("download", "application/octet-stream", pdfHead, UploadTab.FILES),
        )
        assertEquals(
            Classified.Rejected(UploadRejection.UNSUPPORTED),
            UploadKind.classify("download", "application/octet-stream", textHead, UploadTab.FILES),
        )
    }

    @Test
    fun unsupportedTypes_andMissingName_areRejected() {
        assertEquals(
            Classified.Rejected(UploadRejection.UNSUPPORTED),
            UploadKind.classify("lecture.docx", "application/vnd.openxmlformats", textHead, UploadTab.FILES),
        )
        assertEquals(
            Classified.Rejected(UploadRejection.UNSUPPORTED),
            UploadKind.classify(null, "text/plain", textHead, UploadTab.FILES),
        )
    }
}
