package com.ezragubbay.quire.core.download

import java.io.File

/** Where downloads live: `<filesDir>/downloads/<courseSlug>/<fileId>.<ext>` (+ `.part` while in flight). */
class DownloadPaths(
    filesDir: File,
) {
    val root: File = File(filesDir, "downloads")

    fun finalFile(
        courseSlug: String,
        fileId: String,
        format: String,
    ): File = File(courseDir(courseSlug), "${safe(fileId)}.${extension(format)}")

    fun partFile(
        courseSlug: String,
        fileId: String,
        format: String,
    ): File = File(courseDir(courseSlug), "${safe(fileId)}.${extension(format)}.part")

    private fun courseDir(courseSlug: String) = File(root, safe(courseSlug))

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        fun extension(format: String) =
            when (format) {
                "pdf" -> "pdf"
                "markdown" -> "md"
                else -> "bin"
            }
    }
}
