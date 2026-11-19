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
