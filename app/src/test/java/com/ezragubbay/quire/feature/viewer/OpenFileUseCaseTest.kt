package com.ezragubbay.quire.feature.viewer

import com.ezragubbay.quire.core.data.course.CourseFile
import com.ezragubbay.quire.core.designsystem.theme.FileType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class OpenFileUseCaseTest {
    private fun file(
        hasPdf: Boolean = false,
        hasOriginal: Boolean = false,
        hasMarkdown: Boolean = false,
    ) = CourseFile(
        fileId = "f1",
        courseSlug = "c",
        title = "Syllabus.docx",
        type = FileType.OTHER,
        sessionNumber = null,
        sessionTitle = null,
        folder = null,
        pageCount = null,
        sizeBytes = 1,
        hasPdf = hasPdf,
        hasOriginal = hasOriginal,
        hasMarkdown = hasMarkdown,
        updatedAt = "2026-01-01T00:00:00Z",
    )

    private val disk = File("/tmp/f1.pdf")

    @Test fun pdfNotDownloaded_streamsToCache() =
        assertEquals(
            OpenPlan.ViewPdf(OpenSource.Stream("pdf")),
            OpenFileUseCase.plan(file(hasPdf = true), null),
        )

    @Test fun pdfDownloaded_opensFromDisk() =
        assertEquals(
            OpenPlan.ViewPdf(OpenSource.Local(disk)),
            OpenFileUseCase.plan(file(hasPdf = true), disk),
        )

    @Test fun pdfWithOriginal_prefersPdfViewer() =
        assertEquals(
            OpenPlan.ViewPdf(OpenSource.Stream("pdf")),
            OpenFileUseCase.plan(file(hasPdf = true, hasOriginal = true), null),
        )

    @Test fun originalOnly_offersOpenWith_streamingOriginal() =
        assertEquals(
            OpenPlan.OpenWith(OpenSource.Stream("original"), "Syllabus.docx"),
            OpenFileUseCase.plan(file(hasOriginal = true), null),
        )

    @Test fun originalOnlyDownloaded_openWithUsesDisk() =
        assertEquals(
            OpenPlan.OpenWith(OpenSource.Local(disk), "Syllabus.docx"),
            OpenFileUseCase.plan(file(hasOriginal = true), disk),
        )

    @Test fun markdownOnly_isUnsupportedHere() =
        assertEquals(OpenPlan.Unsupported, OpenFileUseCase.plan(file(hasMarkdown = true), null))

    @Test fun unknownMetadata_downloaded_opensFromDisk() =
        assertEquals(OpenPlan.ViewPdf(OpenSource.Local(disk)), OpenFileUseCase.plan(null, disk))

    @Test fun unknownMetadata_notDownloaded_streamsPdf() =
        assertEquals(OpenPlan.ViewPdf(OpenSource.Stream("pdf")), OpenFileUseCase.plan(null, null))

    @Test fun mime_knownAndUnknown() {
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            OpenWithMime.forName("a.DOCX"),
        )
        assertEquals(OpenWithMime.UNKNOWN, OpenWithMime.forName("noext"))
    }
}
