package com.ezragubbay.quire.core.data.course

import com.ezragubbay.quire.core.designsystem.theme.FileType

data class Course(
    val slug: String,
    val id: String,
    val name: String,
    val code: String?,
    val semester: String?,
    /** Sortable semester grouping key (larger = more recent); courses with equal keys share a header. */
    val semesterKey: Int,
    val type: String,
    val fileCount: Int,
)

/** Per-kind / per-type counts from the course detail. Keys are the server labels. */
data class CourseCounts(
    val sessions: Map<String, Int>,
    val files: Map<String, Int>,
    val notes: Int,
)

data class CourseDetail(
    val course: Course,
    val counts: CourseCounts,
)

data class CourseSession(
    val courseSlug: String,
    /** `lecture` or `recitation`. */
    val kind: String,
    val number: Int,
    val title: String,
    /** ISO date (`2026-11-02`) or null. */
    val date: String?,
    val fileIds: List<String>,
    val noteId: String?,
)

data class CourseFile(
    val fileId: String,
    val courseSlug: String,
    val title: String,
    val type: FileType,
    val sessionNumber: Int?,
    val sessionTitle: String?,
    val folder: String?,
    val pageCount: Int?,
    val sizeBytes: Long,
    val hasPdf: Boolean,
    val hasOriginal: Boolean,
    val hasMarkdown: Boolean,
    /** ISO-8601 instant. */
    val updatedAt: String,
)

data class CourseNote(
    val id: String,
    val courseSlug: String,
    val slug: String,
    val title: String,
    val sessionKind: String?,
    val sessionNumber: Int?,
    val updatedAt: String,
)
