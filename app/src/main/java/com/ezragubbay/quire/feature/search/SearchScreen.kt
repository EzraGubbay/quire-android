package com.ezragubbay.quire.feature.search

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.data.course.Course
import com.ezragubbay.quire.core.data.course.CourseFile
import com.ezragubbay.quire.core.designsystem.component.EmptyState
import com.ezragubbay.quire.core.designsystem.component.FileTypeChip
import com.ezragubbay.quire.core.designsystem.component.SectionHeader
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme

/**
 * Full-screen search over the cached courses and files (works offline). A course result opens that course;
 * a file result opens its course (the viewer arrives with the reader stories).
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenCourse: (slug: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchContent(
        query = query,
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onBack = onBack,
        onOpenCourse = {
            viewModel.onResultOpened()
            onOpenCourse(it)
        },
        onClearRecents = viewModel::clearRecents,
    )
}

@Composable
internal fun SearchContent(
    query: String,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenCourse: (String) -> Unit,
    onClearRecents: () -> Unit,
) {
    val spacing = QuireTheme.spacing
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize().testTag("screen-search")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("action-back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.search_back))
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_field_hint)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.testTag("search-clear")) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear_query))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).focusRequester(focus).testTag("search-field"),
            )
        }
        when (state) {
            is SearchUiState.Recents -> RecentList(state.queries, onQueryChange, onClearRecents)
            SearchUiState.NoResults ->
                Box(Modifier.fillMaxSize().testTag("search-empty")) {
                    EmptyState(
                        title = stringResource(R.string.search_no_results),
                        message = stringResource(R.string.search_no_results_message),
                    )
                }
            is SearchUiState.Results -> ResultList(query.trim(), state, onOpenCourse)
        }
    }
}

@Composable
private fun RecentList(
    queries: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (queries.isEmpty()) {
        Box(Modifier.fillMaxSize()) { EmptyState(title = stringResource(R.string.search_hint_empty)) }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("search-recents")) {
        item(key = "recent-header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionHeader(stringResource(R.string.search_recent_title)) }
                TextButton(onClick = onClear, modifier = Modifier.testTag("search-recent-clear")) {
                    Text(stringResource(R.string.search_recent_clear))
                }
            }
        }
        items(queries.size, key = { "recent-${queries[it]}" }) { n ->
            Text(
                queries[n],
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier
                        .testTag("search-recent-$n")
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onPick(queries[n]) }
                        .padding(horizontal = QuireTheme.spacing.md, vertical = QuireTheme.spacing.sm),
            )
        }
    }
}

@Composable
private fun ResultList(
    query: String,
    state: SearchUiState.Results,
    onOpenCourse: (String) -> Unit,
) {
    val spacing = QuireTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("search-results"),
        contentPadding = PaddingValues(bottom = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        if (state.courses.isNotEmpty()) {
            item(key = "section-courses") {
                Box(Modifier.testTag("search-section-courses")) {
                    SectionHeader(stringResource(R.string.search_section_courses))
                }
            }
            items(state.courses, key = { "course-${it.slug}" }) { course ->
                CourseResult(course, query) { onOpenCourse(course.slug) }
            }
        }
        if (state.files.isNotEmpty()) {
            item(key = "section-files") {
                Box(Modifier.testTag("search-section-files")) {
                    SectionHeader(stringResource(R.string.search_section_files))
                }
            }
            items(state.files, key = { "file-${it.fileId}" }) { file ->
                FileResult(file, query) { onOpenCourse(file.courseSlug) }
            }
        }
    }
}

@Composable
private fun CourseResult(
    course: Course,
    query: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .testTag("search-result-course-${course.slug}")
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = QuireTheme.spacing.md, vertical = QuireTheme.spacing.sm),
    ) {
        Text(highlight(course.name, query), style = MaterialTheme.typography.titleMedium)
        course.code?.takeIf(String::isNotBlank)?.let {
            Text(
                highlight(it, query),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileResult(
    file: CourseFile,
    query: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .testTag("search-result-file-${file.fileId}")
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = QuireTheme.spacing.md, vertical = QuireTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(QuireTheme.spacing.xs),
    ) {
        Text(highlight(file.title, query), style = MaterialTheme.typography.titleMedium)
        FileTypeChip(file.type)
    }
}

/** [text] with the first case-insensitive occurrence of [query] in bold. */
@Composable
private fun highlight(
    text: String,
    query: String,
): AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        val at = if (query.isEmpty()) -1 else text.indexOf(query, ignoreCase = true)
        if (at < 0) {
            append(text)
        } else {
            append(text.substring(0, at))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                append(text.substring(at, at + query.length))
            }
            append(text.substring(at + query.length))
        }
    }
}
