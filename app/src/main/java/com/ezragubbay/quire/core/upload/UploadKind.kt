package com.ezragubbay.quire.core.upload

/** What the file is on the wire. */
enum class UploadFormat(
    val wire: String,
    val contentType: String,
) {
    PDF("pdf", "application/pdf"),
    MARKDOWN("markdown", "text/markdown"),
}

/** What the server turns the upload into: the `kind` query value. */
enum class UploadTarget(
    val wire: String,
) {
    DOCUMENT("document"),
    NOTE("note"),
}

/** The course tab the file was picked on. */
enum class UploadTab { FILES, NOTES }

/** Size ceilings the server enforces (mobile API 1.2.0). */
object UploadLimits {
    const val PDF_MAX_BYTES = 100L * 1024 * 1024
    const val MARKDOWN_MAX_BYTES = 2L * 1024 * 1024

    fun maxFor(format: UploadFormat) = if (format == UploadFormat.PDF) PDF_MAX_BYTES else MARKDOWN_MAX_BYTES
}

/** Why a picked file was not enqueued. */
enum class UploadRejection {
    UNSUPPORTED,
    NOT_A_PDF,
    PDF_TOO_LARGE,
    MARKDOWN_TOO_LARGE,
    NOT_UTF8,
    EMPTY,
    UNREADABLE,
}

sealed interface Classified {
    /**
     * [savedToDocuments] is true when a PDF was picked on the Notes tab: notes are Markdown, so the PDF is uploaded
     * as a document and the UI says so.
     */
    data class Accepted(
        val format: UploadFormat,
        val target: UploadTarget,
        val savedToDocuments: Boolean,
    ) : Classified

    data class Rejected(
        val reason: UploadRejection,
    ) : Classified
}

/** Decides what a picked file is. Pure: the stager supplies the display name, MIME type and first bytes. */
object UploadKind {
    private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val MARKDOWN_MIMES = setOf("text/markdown", "text/x-markdown")

    /**
     * The extension wins over the MIME type: the system picker often reports a `.md` file as `text/plain` or
     * `application/octet-stream`. A PDF must also start with `%PDF-`.
     */
    @Suppress("ReturnCount") // guard clauses: each early return is a rejection
    fun classify(
        displayName: String?,
        mime: String?,
        head: ByteArray,
        tab: UploadTab,
    ): Classified {
        val name = displayName.orEmpty().lowercase()
        val type =
            mime
                .orEmpty()
                .substringBefore(';')
                .trim()
                .lowercase()
        val format =
            when {
                name.endsWith(".pdf") -> UploadFormat.PDF
                name.endsWith(".md") || name.endsWith(".markdown") -> UploadFormat.MARKDOWN
                type == "application/pdf" -> UploadFormat.PDF
                type in MARKDOWN_MIMES -> UploadFormat.MARKDOWN
                // A provider may report a PDF as an opaque binary with no extension: the magic bytes decide.
                head.startsWithPdfMagic() -> UploadFormat.PDF
                else -> return Classified.Rejected(UploadRejection.UNSUPPORTED)
            }
        if (format == UploadFormat.PDF && !head.startsWithPdfMagic()) {
            return Classified.Rejected(UploadRejection.NOT_A_PDF)
        }
        return when {
            format == UploadFormat.PDF ->
                Classified.Accepted(format, UploadTarget.DOCUMENT, savedToDocuments = tab == UploadTab.NOTES)
            tab == UploadTab.NOTES -> Classified.Accepted(format, UploadTarget.NOTE, savedToDocuments = false)
            else -> Classified.Accepted(format, UploadTarget.DOCUMENT, savedToDocuments = false)
        }
    }

    private fun ByteArray.startsWithPdfMagic() =
        size >= PDF_MAGIC.size && PDF_MAGIC.indices.all { this[it] == PDF_MAGIC[it] }
}
