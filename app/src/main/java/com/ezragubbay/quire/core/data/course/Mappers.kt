@file:Suppress("TooManyFunctions") // one tiny mapper per DTO/entity/model; splitting adds no clarity

package com.ezragubbay.quire.core.data.course

import com.ezragubbay.quire.core.database.CourseCountsEntity
import com.ezragubbay.quire.core.database.CourseEntity
import com.ezragubbay.quire.core.database.CourseFileEntity
import com.ezragubbay.quire.core.database.CourseSessionEntity
import com.ezragubbay.quire.core.database.NoteEntity
import com.ezragubbay.quire.core.database.semesterKey
import com.ezragubbay.quire.core.designsystem.theme.FileType
import com.ezragubbay.quire.core.network.CourseCountsDto
import com.ezragubbay.quire.core.network.CourseFileDto
import com.ezragubbay.quire.core.network.CourseSessionDto
import com.ezragubbay.quire.core.network.CourseSummaryDto
import com.ezragubbay.quire.core.network.NoteDto
import com.ezragubbay.quire.core.network.QuireJson
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val countsSerializer = MapSerializer(String.serializer(), Int.serializer())

/** Server label -> [FileType]; unknown labels fall back to [FileType.OTHER] so a new server type never crashes. */
fun fileTypeFromWire(label: String): FileType =
    FileType.entries.firstOrNull { it.name.equals(label, true) } ?: FileType.OTHER

internal fun CourseSummaryDto.toEntity() =
    CourseEntity(slug, id, name, code, semester, semesterKey(semester), type, fileCount)

internal fun CourseCountsDto.toEntity(slug: String) =
    CourseCountsEntity(
        slug,
        QuireJson.encodeToString(countsSerializer, sessions),
        QuireJson.encodeToString(countsSerializer, files),
        notes,
    )

internal fun CourseSessionDto.toEntity(slug: String) =
    CourseSessionEntity(slug, kind, number, title, date, fileIds.joinToString(","), noteId)

internal fun CourseFileDto.toEntity(slug: String) =
    CourseFileEntity(
        fileId,
        slug,
        title,
        type,
        session?.number,
        session?.title,
        folder,
        pageCount,
        sizeBytes,
        hasPdf,
        hasOriginal,
        hasMarkdown,
        updatedAt,
    )

internal fun NoteDto.toEntity(slug: String) =
    NoteEntity(id, slug, this.slug, title, session?.kind, session?.number, updatedAt)

internal fun CourseEntity.toModel() = Course(slug, id, name, code, semester, semesterKey, type, fileCount)

internal fun CourseCountsEntity?.toModel() =
    CourseCounts(
        sessions = this?.let { QuireJson.decodeFromString(countsSerializer, sessionCounts) }.orEmpty(),
        files = this?.let { QuireJson.decodeFromString(countsSerializer, fileCounts) }.orEmpty(),
        notes = this?.noteCount ?: 0,
    )

internal fun CourseSessionEntity.toModel() =
    CourseSession(courseSlug, kind, number, title, date, fileIds.split(',').filter(String::isNotEmpty), noteId)

internal fun CourseFileEntity.toModel() =
    CourseFile(
        fileId,
        courseSlug,
        title,
        fileTypeFromWire(type),
        sessionNumber,
        sessionTitle,
        folder,
        pageCount,
        sizeBytes,
        hasPdf,
        hasOriginal,
        hasMarkdown,
        updatedAt,
    )

internal fun NoteEntity.toModel() = CourseNote(id, courseSlug, slug, title, sessionKind, sessionNumber, updatedAt)
