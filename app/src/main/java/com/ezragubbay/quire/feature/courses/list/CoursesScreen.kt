package com.ezragubbay.quire.feature.courses.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.data.course.Course
import com.ezragubbay.quire.core.designsystem.component.CourseCard
import com.ezragubbay.quire.core.designsystem.component.EmptyState
import com.ezragubbay.quire.core.designsystem.component.ErrorState
import com.ezragubbay.quire.core.designsystem.component.LoadingSkeleton
import com.ezragubbay.quire.core.designsystem.component.QuirePullToRefresh
import com.ezragubbay.quire.core.designsystem.component.SectionHeader
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme
import com.ezragubbay.quire.push.NotificationPermissionCard

/** The Courses tab: semester-grouped course list with pull-to-refresh and loading / empty / error / offline states. */
@Composable
fun CoursesScreen(
    onOpenCourse: (slug: String) -> Unit,
    viewModel: CoursesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.onStart() }
    }
    Column(Modifier.fillMaxSize()) {
        // One-time "allow notifications" card after the first pairing (QMOB-E8-S3); renders nothing otherwise.
        NotificationPermissionCard()
        CoursesContent(state, onOpenCourse, viewModel::refresh, viewModel::retry)
    }
}

@Composable
internal fun CoursesContent(
    state: CoursesUiState,
    onOpenCourse: (slug: String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize().testTag("screen-courses")) {
        when (state) {
            CoursesUiState.Loading -> Box(Modifier.testTag("courses-skeleton")) { LoadingSkeleton() }
            CoursesUiState.Empty ->
                Box(Modifier.fillMaxSize().testTag("courses-empty")) {
                    EmptyState(
                        title = stringResource(R.string.courses_empty_title),
                        message = stringResource(R.string.courses_empty_message),
                    )
                }
            CoursesUiState.Error ->
                Box(Modifier.fillMaxSize().testTag("courses-error")) {
                    ErrorState(
                        message = stringResource(R.string.courses_error),
                        onRetry = onRetry,
                        retryTag = "action-retry",
                    )
                }
            is CoursesUiState.Content -> CourseGroups(state, onOpenCourse, onRefresh)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CourseGroups(
    state: CoursesUiState.Content,
    onOpenCourse: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val spacing = QuireTheme.spacing
    Column(Modifier.fillMaxSize()) {
        if (state.offline) OfflineBanner()
        Box(Modifier.fillMaxSize().testTag("courses-refresh")) {
            // Present only while a refresh is in flight; lets tests await the refresh settling.
            if (state.refreshing) Box(Modifier.size(1.dp).testTag("courses-refreshing"))
            QuirePullToRefresh(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("course-list"),
                    contentPadding = PaddingValues(bottom = spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    state.groups.forEach { group ->
                        stickyHeader(key = "group-${group.tag}") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .testTag("course-group-${group.tag}"),
                            ) {
                                SectionHeader(group.label ?: stringResource(R.string.courses_other_semester))
                            }
                        }
                        items(group.courses, key = { it.slug }) { course ->
                            Box(
                                Modifier
                                    .padding(horizontal = spacing.md)
                                    .testTag("course-card-${course.slug}"),
                            ) {
                                CourseCard(
                                    title = course.name,
                                    subtitle = course.subtitle(),
                                    onClick = { onOpenCourse(course.slug) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Course.subtitle(): String {
    val files = pluralStringResource(R.plurals.courses_file_count, fileCount, fileCount)
    return if (code.isNullOrBlank()) files else stringResource(R.string.courses_card_subtitle, code, files)
}

@Composable
private fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("offline-banner"),
    ) {
        Text(
            stringResource(R.string.courses_offline),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = QuireTheme.spacing.md, vertical = QuireTheme.spacing.sm),
        )
    }
}
