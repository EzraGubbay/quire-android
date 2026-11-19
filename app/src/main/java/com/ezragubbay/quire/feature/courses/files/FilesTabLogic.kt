package com.ezragubbay.quire.feature.courses.files

import com.ezragubbay.quire.core.data.course.CourseFile
import com.ezragubbay.quire.core.designsystem.theme.FileType

/** How the Files tab orders files inside each type group. */
enum class FileSort(
    val key: String,
) {
    COURSE_ORDER("course"),
    TITLE("title"),
    RECENT("recent"),
    ;

    companion object {
        fun fromKey(key: String?): FileSort = entries.firstOrNull { it.key == key } ?: COURSE_ORDER
    }
}

/** One type's files, already filtered and sorted. */
data class FileGroup(
    val type: FileType,
    val files: List<CourseFile>,
)

/** What the Files tab renders; [groups] is empty when nothing matches (the empty-result state). */
data class FilesView(
    val selectedTypes: Set<FileType>,
    val sort: FileSort,
    val groups: List<FileGroup>,
    /** Types that have at least one file, in display order: the filter chips offered. */
    val availableTypes: List<FileType>,
)

/** The `{type}` test-tag segment (`filter-chip-{type}`, `file-group-{type}`). */
val FileType.tag: String get() = name.lowercase()

/** Types selected from a persisted set of [FileType.tag] values; unknown entries are ignored. */
fun parseSelectedTypes(tags: Collection<String>?): Set<FileType> =
    FileType.entries.filter { it.tag in (tags ?: emptyList()) }.toSet()

/** Keeps files of [selected] types; an empty selection keeps everything. */
fun List<CourseFile>.filterByTypes(selected: Set<FileType>): List<CourseFile> =
    if (selected.isEmpty()) this else filter { it.type in selected }

/** Orders files; every sort is stable, and course order is the incoming (server) order. */
fun List<CourseFile>.sortedBy(sort: FileSort): List<CourseFile> =
    when (sort) {
        FileSort.COURSE_ORDER -> this
        FileSort.TITLE -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        FileSort.RECENT -> sortedByDescending { it.updatedAt }
    }

/** Filters, sorts and groups [files] by type in [FileType] order, dropping empty groups. */
fun buildFilesView(
    files: List<CourseFile>,
    selected: Set<FileType>,
    sort: FileSort,
): FilesView {
    val sorted = files.filterByTypes(selected).sortedBy(sort)
    return FilesView(
        selectedTypes = selected,
        sort = sort,
        groups =
            FileType.entries.mapNotNull { type ->
                sorted.filter { it.type == type }.takeIf { it.isNotEmpty() }?.let { FileGroup(type, it) }
            },
        availableTypes = FileType.entries.filter { type -> files.any { it.type == type } },
    )
}

/** A file's downloadable formats, derived from the `has*` flags. */
enum class FileFormat { PDF, ORIGINAL, MARKDOWN }

fun CourseFile.formats(): List<FileFormat> =
    listOfNotNull(
        FileFormat.PDF.takeIf { hasPdf },
        FileFormat.ORIGINAL.takeIf { hasOriginal },
        FileFormat.MARKDOWN.takeIf { hasMarkdown },
    )

/** Session or folder the file belongs to, for the info sheet; null when it has neither. */
fun CourseFile.location(): String? = sessionTitle?.takeIf { it.isNotBlank() } ?: folder?.takeIf { it.isNotBlank() }
