package com.ezragubbay.quire.feature.viewer

import com.ezragubbay.quire.core.data.course.CourseFile
import java.io.File

/** Where the bytes for a file come from when it is opened. */
sealed interface OpenSource {
    /** Already downloaded (persisted): open straight from disk, no network. */
    data class Local(
        val file: File,
    ) : OpenSource

    /** Not downloaded: stream [format] into the evictable viewer cache (NOT a persisted download). */
    data class Stream(
        val format: String,
    ) : OpenSource
}

/** What tapping a file does. */
sealed interface OpenPlan {
    /** Render the PDF in the in-app viewer. */
    data class ViewPdf(
        val source: OpenSource,
    ) : OpenPlan

    /** No in-app view (original only): offer 'Open with...' on the original. */
    data class OpenWith(
        val source: OpenSource,
        val title: String,
    ) : OpenPlan

    /** Nothing the viewer can show (e.g. Markdown-only: that opens in the notes reader). */
    data object Unsupported : OpenPlan
}

const val FORMAT_PDF = "pdf"
const val FORMAT_ORIGINAL = "original"

/** Decides how a file opens. Pure: all inputs are passed in so every branch is unit-testable. */
object OpenFileUseCase {
    /**
     * [file] is the cached metadata (null when unknown, e.g. never synced); [local] is the persisted download, if
     * any. A downloaded file always opens from disk; otherwise a PDF streams to the cache.
     */
    fun plan(
        file: CourseFile?,
        local: File?,
    ): OpenPlan {
        if (file == null) {
            // Unknown metadata: a download proves it opens; otherwise assume the PDF rendering (as decks do).
            return OpenPlan.ViewPdf(local?.let(OpenSource::Local) ?: OpenSource.Stream(FORMAT_PDF))
        }
        return when {
            file.hasPdf -> OpenPlan.ViewPdf(local?.let(OpenSource::Local) ?: OpenSource.Stream(FORMAT_PDF))
            file.hasOriginal ->
                OpenPlan.OpenWith(local?.let(OpenSource::Local) ?: OpenSource.Stream(FORMAT_ORIGINAL), file.title)
            else -> OpenPlan.Unsupported
        }
    }
}
