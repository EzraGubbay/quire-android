package com.ezragubbay.quire.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "courses", indices = [Index("semesterKey")])
data class CourseEntity(
    @PrimaryKey val slug: String,
    val id: String,
    val name: String,
    val code: String?,
    val semester: String?,
    /** Sortable grouping key derived from [semester]; larger is more recent, 0 when unknown. */
    val semesterKey: Int,
    val type: String,
    val fileCount: Int,
)

/** Detail-only counts, kept apart from [CourseEntity] so a list refresh never wipes them. */
@Entity(
    tableName = "course_counts",
    foreignKeys = [ForeignKey(CourseEntity::class, ["slug"], ["courseSlug"], onDelete = ForeignKey.CASCADE)],
)
data class CourseCountsEntity(
    @PrimaryKey val courseSlug: String,
    /** JSON object of kind -> count. */
    val sessionCounts: String,
    /** JSON object of type -> count. */
    val fileCounts: String,
    val noteCount: Int,
)

@Entity(
    tableName = "sessions",
    primaryKeys = ["courseSlug", "kind", "number"],
    foreignKeys = [ForeignKey(CourseEntity::class, ["slug"], ["courseSlug"], onDelete = ForeignKey.CASCADE)],
)
data class CourseSessionEntity(
    val courseSlug: String,
    val kind: String,
    val number: Int,
    val title: String,
    val date: String?,
    /** Comma-joined file ids (UUIDs, so never contain a comma). */
    val fileIds: String,
    val noteId: String?,
)

@Entity(
    tableName = "files",
    indices = [Index("courseSlug")],
    foreignKeys = [ForeignKey(CourseEntity::class, ["slug"], ["courseSlug"], onDelete = ForeignKey.CASCADE)],
)
data class CourseFileEntity(
    @PrimaryKey val fileId: String,
    val courseSlug: String,
    val title: String,
    val type: String,
    val sessionNumber: Int?,
    val sessionTitle: String?,
    val folder: String?,
    val pageCount: Int?,
    val sizeBytes: Long,
    val hasPdf: Boolean,
    val hasOriginal: Boolean,
    val hasMarkdown: Boolean,
    val updatedAt: String,
)

@Entity(
    tableName = "notes",
    indices = [Index("courseSlug")],
    foreignKeys = [ForeignKey(CourseEntity::class, ["slug"], ["courseSlug"], onDelete = ForeignKey.CASCADE)],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val courseSlug: String,
    val slug: String,
    val title: String,
    val sessionKind: String?,
    val sessionNumber: Int?,
    val updatedAt: String,
)

/** Persisted state of one offline download. Room is the source of truth; the worker and repository write it. */
@Entity(tableName = "downloads", indices = [Index("courseSlug")])
data class DownloadEntity(
    @PrimaryKey val fileId: String,
    val courseSlug: String,
    /** `pdf`, `original` or `markdown` (the content endpoint's `format` query value). */
    val format: String,
    /** One of [DownloadEntity.QUEUED], [RUNNING], [DONE], [FAILED]. */
    val state: String,
    /** Bytes on disk so far (the `.part` length while running, the file size once done). */
    val progressBytes: Long,
    /** Full size when known, else [UNKNOWN_TOTAL]. */
    val totalBytes: Long,
    /** Absolute path of the finished file; set only when [state] is [DONE]. */
    val localPath: String?,
    /** Validator of the bytes in the `.part` file; used with `If-Range` to resume. */
    val etag: String?,
    val error: String?,
    val updatedAt: Long,
) {
    companion object {
        const val QUEUED = "QUEUED"
        const val RUNNING = "RUNNING"
        const val DONE = "DONE"
        const val FAILED = "FAILED"
        const val UNKNOWN_TOTAL = -1L
    }
}

/** The last-read page (0-based) of one file in the PDF viewer. */
@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey val fileId: String,
    val page: Int,
    val updatedAt: Long,
)
