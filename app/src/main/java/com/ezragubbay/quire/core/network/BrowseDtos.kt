package com.ezragubbay.quire.core.network

import kotlinx.serialization.Serializable

/** `GET /courses` item. `type` is the server label as sent; unknown values are tolerated. */
@Serializable
data class CourseSummaryDto(
    val slug: String,
    val id: String,
    val name: String,
    val code: String? = null,
    val semester: String? = null,
    val type: String = "other",
    val fileCount: Int = 0,
)

/** Per-kind / per-type counts of `GET /courses/{course}`. Keys are server labels (`lecture`, `homework`, ...). */
@Serializable
data class CourseCountsDto(
    val sessions: Map<String, Int> = emptyMap(),
    val files: Map<String, Int> = emptyMap(),
    val notes: Int = 0,
)

/** `GET /courses/{course}`: the [CourseSummaryDto] fields (flat, as on the wire) plus [counts]. */
@Serializable
data class CourseDetailDto(
    val slug: String,
    val id: String,
    val name: String,
    val code: String? = null,
    val semester: String? = null,
    val type: String = "other",
    val fileCount: Int = 0,
    val counts: CourseCountsDto = CourseCountsDto(),
) {
    fun toSummary() = CourseSummaryDto(slug, id, name, code, semester, type, fileCount)
}

/** `GET /courses/{course}/sessions` item; `kind` is `lecture` or `recitation`. */
@Serializable
data class CourseSessionDto(
    val kind: String,
    val number: Int,
    val title: String,
    val date: String? = null,
    val fileIds: List<String> = emptyList(),
    val noteId: String? = null,
)

@Serializable
data class FileSessionRefDto(
    val number: Int,
    val title: String,
)

/** `GET /courses/{course}/files` item. `type` is one of lecture/recitation/homework/exam/book/paper/other. */
@Serializable
data class CourseFileDto(
    val fileId: String,
    val title: String,
    val type: String,
    val session: FileSessionRefDto? = null,
    val folder: String? = null,
    val pageCount: Int? = null,
    val sizeBytes: Long = 0,
    val hasPdf: Boolean = false,
    val hasOriginal: Boolean = false,
    val hasMarkdown: Boolean = false,
    val updatedAt: String = "",
)

@Serializable
data class NoteSessionRefDto(
    val kind: String,
    val number: Int,
)

/** `GET /courses/{course}/notes` item. */
@Serializable
data class NoteDto(
    val id: String,
    val slug: String,
    val title: String,
    val session: NoteSessionRefDto? = null,
    val updatedAt: String = "",
)
