package com.ezragubbay.quire.feature.viewer

/** MIME type for an original file, from its name; the wildcard type when unknown so the chooser still offers apps. */
object OpenWithMime {
    private val types =
        mapOf(
            "pdf" to "application/pdf",
            "doc" to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "ppt" to "application/vnd.ms-powerpoint",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "xls" to "application/vnd.ms-excel",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "odt" to "application/vnd.oasis.opendocument.text",
            "odp" to "application/vnd.oasis.opendocument.presentation",
            "txt" to "text/plain",
            "md" to "text/markdown",
            "csv" to "text/csv",
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "zip" to "application/zip",
        )

    const val UNKNOWN = "*/*"

    fun forName(name: String): String = types[name.substringAfterLast('.', "").lowercase()] ?: UNKNOWN
}
