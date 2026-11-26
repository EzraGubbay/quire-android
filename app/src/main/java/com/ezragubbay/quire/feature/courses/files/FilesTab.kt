package com.ezragubbay.quire.feature.courses.files

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.data.course.CourseFile
import com.ezragubbay.quire.core.designsystem.component.EmptyState
import com.ezragubbay.quire.core.designsystem.component.FileTypeChip
import com.ezragubbay.quire.core.designsystem.component.fileTypeLabelRes
import com.ezragubbay.quire.core.designsystem.theme.FileType
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme

@StringRes
private fun groupLabelRes(type: FileType): Int =
    when (type) {
        FileType.LECTURE -> R.string.files_group_lecture
        FileType.RECITATION -> R.string.files_group_recitation
        FileType.HOMEWORK -> R.string.files_group_homework
        FileType.EXAM -> R.string.files_group_exam
        FileType.BOOK -> R.string.files_group_book
        FileType.PAPER -> R.string.files_group_paper
        FileType.OTHER -> R.string.files_group_other
    }

@StringRes
private fun sortLabelRes(sort: FileSort): Int =
    when (sort) {
        FileSort.COURSE_ORDER -> R.string.files_sort_course
        FileSort.TITLE -> R.string.files_sort_title
        FileSort.RECENT -> R.string.files_sort_recent
    }

@StringRes
private fun formatLabelRes(format: FileFormat): Int =
    when (format) {
        FileFormat.PDF -> R.string.files_format_pdf
        FileFormat.ORIGINAL -> R.string.files_format_original
        FileFormat.MARKDOWN -> R.string.files_format_markdown
    }

/**
 * The course screen's Files tab: type filter chips and a sort control above a list grouped by type.
 * A row opens the file ([onOpenFile]); long-press or the info button opens the info sheet.
 */
@Composable
fun FilesTab(
    view: FilesView,
    noFiles: Boolean,
    onToggleType: (FileType) -> Unit,
    onSelectSort: (FileSort) -> Unit,
    onOpenFile: (String) -> Unit,
    /** Extra space under the list, so a floating button never covers the last row. */
    bottomPadding: Dp = 0.dp,
) {
    if (noFiles) {
        EmptyState(stringResource(R.string.course_files_empty), Modifier.testTag("file-list"))
        return
    }
    var infoFileId by rememberSaveable { mutableStateOf<String?>(null) }
    val spacing = QuireTheme.spacing
    Column(Modifier.fillMaxSize()) {
        FilterBar(view, onToggleType, onSelectSort)
        if (view.groups.isEmpty()) {
            EmptyState(stringResource(R.string.files_filtered_empty), Modifier.testTag("file-list"))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("file-list"),
                contentPadding = PaddingValues(bottom = spacing.sm + bottomPadding),
            ) {
                view.groups.forEach { group ->
                    item(key = "group-${group.type.tag}") { GroupHeader(group.type) }
                    items(group.files.size, key = { group.files[it].fileId }) { i ->
                        FileRow(group.files[i], onOpenFile, onInfo = { infoFileId = it })
                    }
                }
            }
        }
    }
    val infoFile = view.groups.firstNotNullOfOrNull { g -> g.files.firstOrNull { it.fileId == infoFileId } }
    infoFile?.let { FileInfoSheet(it, onDismiss = { infoFileId = null }) }
}

@Composable
private fun FilterBar(
    view: FilesView,
    onToggleType: (FileType) -> Unit,
    onSelectSort: (FileSort) -> Unit,
) {
    val spacing = QuireTheme.spacing
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            view.availableTypes.forEach { type ->
                FilterChip(
                    selected = type in view.selectedTypes,
                    onClick = { onToggleType(type) },
                    label = { Text(stringResource(fileTypeLabelRes(type))) },
                    modifier = Modifier.testTag("filter-chip-${type.tag}"),
                )
            }
        }
        SortControl(view.sort, onSelectSort)
    }
}

@Composable
private fun SortControl(
    sort: FileSort,
    onSelect: (FileSort) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(stringResource(sortLabelRes(sort))) },
            modifier = Modifier.testTag("sort-control"),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FileSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(sortLabelRes(option))) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                    modifier = Modifier.testTag("sort-option-${option.key}"),
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(type: FileType) {
    val spacing = QuireTheme.spacing
    Text(
        stringResource(groupLabelRes(type)),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm)
                .semantics { heading() }
                .testTag("file-group-${type.tag}"),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: CourseFile,
    onOpenFile: (String) -> Unit,
    onInfo: (String) -> Unit,
) {
    val spacing = QuireTheme.spacing
    val context = LocalContext.current
    val pages = file.pageCount?.let { pluralStringResource(R.plurals.course_page_count, it, it) }
    val size = Formatter.formatShortFileSize(context, file.sizeBytes)
    Box(Modifier.testTag("file-row-${file.fileId}")) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .combinedClickable(onClick = { onOpenFile(file.fileId) }, onLongClick = { onInfo(file.fileId) })
                    .padding(start = spacing.md, top = spacing.sm, bottom = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(file.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (pages == null) size else stringResource(R.string.course_file_meta, pages, size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FileTypeChip(file.type)
            IconButton(onClick = { onInfo(file.fileId) }, modifier = Modifier.testTag("file-info-${file.fileId}")) {
                Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.files_info_more))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileInfoSheet(
    file: CourseFile,
    onDismiss: () -> Unit,
) {
    val spacing = QuireTheme.spacing
    val context = LocalContext.current
    val formatLabels = file.formats().map { stringResource(formatLabelRes(it)) }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("file-info-sheet")) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = spacing.md).padding(bottom = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                file.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("file-info-title"),
            )
            InfoLine(R.string.files_info_type, stringResource(fileTypeLabelRes(file.type)))
            file.location()?.let { InfoLine(R.string.files_info_location, it) }
            file.pageCount?.let { InfoLine(R.string.files_info_pages, it.toString()) }
            InfoLine(R.string.files_info_size, Formatter.formatShortFileSize(context, file.sizeBytes), "file-info-size")
            InfoLine(
                R.string.files_info_formats,
                if (formatLabels.isEmpty()) {
                    stringResource(R.string.files_info_no_formats)
                } else {
                    formatLabels.joinToString(" \u00b7 ")
                },
                "file-info-formats",
            )
        }
    }
}

@Composable
private fun InfoLine(
    @StringRes label: Int,
    value: String,
    tag: String? = null,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = if (tag != null) Modifier.testTag(tag) else Modifier)
    }
}
