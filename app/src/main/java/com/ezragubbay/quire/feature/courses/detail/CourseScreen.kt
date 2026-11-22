package com.ezragubbay.quire.feature.courses.detail

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.data.course.CourseNote
import com.ezragubbay.quire.core.designsystem.component.EmptyState
import com.ezragubbay.quire.core.designsystem.component.ErrorState
import com.ezragubbay.quire.core.designsystem.component.LoadingSkeleton
import com.ezragubbay.quire.core.designsystem.component.QuireTopBar
import com.ezragubbay.quire.core.designsystem.theme.FileType
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme
import com.ezragubbay.quire.feature.courses.files.FileSort
import com.ezragubbay.quire.feature.courses.files.FilesTab
import com.ezragubbay.quire.feature.downloads.UnpinDialog
import kotlinx.coroutines.launch

private val TABS =
    listOf(
        Triple("tab-sessions", R.string.course_tab_sessions, 0),
        Triple("tab-files", R.string.course_tab_files, 1),
        Triple("tab-notes", R.string.course_tab_notes, 2),
    )

/**
 * The course screen: a collapsing title and three swipeable tabs (Sessions, Files, Notes). Opening a deck or a note
 * reports it through [onOpenFile] / [onOpenNote]; the viewer and reader arrive with E5.
 */
@Composable
fun CourseScreen(
    onBack: () -> Unit,
    onOpenFile: (fileId: String) -> Unit = {},
    onOpenNote: (noteId: String) -> Unit = {},
    onAsk: () -> Unit = {},
    viewModel: CourseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CourseContent(
        state = state,
        onBack = onBack,
        onSelectFilter = viewModel::selectSessionFilter,
        onToggleFileType = viewModel::toggleFileType,
        onSelectFileSort = viewModel::selectFileSort,
        onRetry = viewModel::retry,
        onOpenFile = onOpenFile,
        onOpenNote = onOpenNote,
        onAsk = onAsk,
        onPin = viewModel::pin,
        onUnpin = viewModel::unpin,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseContent(
    state: CourseUiState,
    onBack: () -> Unit,
    onSelectFilter: (SessionKind) -> Unit,
    onToggleFileType: (FileType) -> Unit,
    onSelectFileSort: (FileSort) -> Unit,
    onRetry: () -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onAsk: () -> Unit = {},
    onPin: () -> Unit = {},
    onUnpin: (removeFiles: Boolean) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val content = state as? CourseUiState.Content
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("screen-course").nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            QuireTopBar(
                title = content?.title.orEmpty(),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onAsk, modifier = Modifier.testTag("action-ask")) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.course_ask))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                CourseUiState.Loading -> Box(Modifier.testTag("course-skeleton")) { LoadingSkeleton() }
                CourseUiState.Error ->
                    Box(Modifier.fillMaxSize().testTag("course-error")) {
                        ErrorState(
                            message = stringResource(R.string.course_error),
                            onRetry = onRetry,
                            retryTag = "action-retry",
                        )
                    }
                is CourseUiState.Content ->
                    CourseTabs(
                        state,
                        onSelectFilter,
                        onToggleFileType,
                        onSelectFileSort,
                        onOpenFile,
                        onOpenNote,
                        onPin,
                        onUnpin,
                    )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseTabs(
    state: CourseUiState.Content,
    onSelectFilter: (SessionKind) -> Unit,
    onToggleFileType: (FileType) -> Unit,
    onSelectFileSort: (FileSort) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onPin: () -> Unit,
    onUnpin: (removeFiles: Boolean) -> Unit,
) {
    // rememberPagerState is saveable, so the selected tab survives rotation and process death.
    val pager = rememberPagerState { TABS.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        if (state.offline) OfflineBanner()
        state.subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(horizontal = QuireTheme.spacing.md)
                        .testTag("course-subtitle"),
            )
        }
        PinRow(state.pinned, onPin, onUnpin)
        if (state.capWarning) CapWarning()
        PrimaryTabRow(selectedTabIndex = pager.currentPage, modifier = Modifier.testTag("course-tabs")) {
            TABS.forEach { (tag, label, index) ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(stringResource(label)) },
                    modifier = Modifier.testTag(tag),
                )
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> SessionsTab(state, onSelectFilter, onOpenFile)
                1 -> FilesTab(state.filesView, state.files.isEmpty(), onToggleFileType, onSelectFileSort, onOpenFile)
                else -> NotesTab(state.notes, onOpenNote)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsTab(
    state: CourseUiState.Content,
    onSelectFilter: (SessionKind) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val spacing = QuireTheme.spacing
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(spacing.md)) {
            SessionKind.entries.forEachIndexed { index, kind ->
                SegmentedButton(
                    selected = state.sessionFilter == kind,
                    onClick = { onSelectFilter(kind) },
                    shape = SegmentedButtonDefaults.itemShape(index, SessionKind.entries.size),
                    modifier = Modifier.testTag("sessions-filter-${kind.key}s"),
                ) {
                    Text(
                        stringResource(
                            if (kind ==
                                SessionKind.LECTURE
                            ) {
                                R.string.course_filter_lectures
                            } else {
                                R.string.course_filter_recitations
                            },
                        ),
                    )
                }
            }
        }
        if (state.sessions.isEmpty()) {
            EmptyState(stringResource(R.string.course_sessions_empty), Modifier.testTag("sessions-empty"))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("session-list"),
                contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(state.sessions, key = { it.tag }) { row ->
                    Box(Modifier.testTag("session-row-${row.tag}")) { SessionItem(row, onOpenFile) }
                }
            }
        }
    }
}

@Composable
private fun SessionItem(
    row: SessionRow,
    onOpenFile: (String) -> Unit,
) {
    val spacing = QuireTheme.spacing
    val label =
        stringResource(
            if (row.kind ==
                SessionKind.LECTURE.key
            ) {
                R.string.course_session_lecture
            } else {
                R.string.course_session_recitation
            },
            row.number,
        )
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(row.title, style = MaterialTheme.typography.titleMedium)
                    row.date?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (row.hasNote) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = stringResource(R.string.course_session_has_note),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("session-note-indicator"),
                    )
                }
            }
            row.decks.forEach { deck ->
                AssistChip(
                    onClick = { onOpenFile(deck.fileId) },
                    label = { Text(deck.title ?: deck.fileId) },
                    modifier = Modifier.testTag("session-deck-${deck.fileId}"),
                )
            }
        }
    }
}

@Composable
private fun NotesTab(
    notes: List<CourseNote>,
    onOpenNote: (String) -> Unit,
) {
    if (notes.isEmpty()) {
        EmptyState(stringResource(R.string.course_notes_empty), Modifier.testTag("note-list"))
        return
    }
    val spacing = QuireTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("note-list"),
        contentPadding = PaddingValues(vertical = spacing.sm),
    ) {
        items(notes, key = { it.id }) { note ->
            Box(Modifier.testTag("note-row-${note.id}")) {
                Text(
                    note.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onOpenNote(note.id) }
                            .padding(horizontal = spacing.md, vertical = spacing.md),
                )
            }
        }
    }
}

/** 'Available offline' toggle plus the pinned badge. Unpinning asks whether to keep the downloaded files. */
@Composable
private fun PinRow(
    pinned: Boolean,
    onPin: () -> Unit,
    onUnpin: (removeFiles: Boolean) -> Unit,
) {
    var confirmUnpin by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = QuireTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QuireTheme.spacing.sm),
    ) {
        FilterChip(
            selected = pinned,
            onClick = { if (pinned) confirmUnpin = true else onPin() },
            label = { Text(stringResource(R.string.pin_available_offline)) },
            modifier = Modifier.testTag("action-pin-course"),
        )
        if (pinned) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.pin_badge_description),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("pinned-badge"),
            )
        }
    }
    if (confirmUnpin) {
        UnpinDialog(
            onUnpin = {
                confirmUnpin = false
                onUnpin(it)
            },
            onDismiss = { confirmUnpin = false },
        )
    }
}

@Composable
private fun CapWarning() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().testTag("storage-cap-warning"),
    ) {
        Text(
            stringResource(R.string.pin_cap_warning),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = QuireTheme.spacing.md, vertical = QuireTheme.spacing.sm),
        )
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("offline-banner"),
    ) {
        Text(
            stringResource(R.string.course_offline),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = QuireTheme.spacing.md, vertical = QuireTheme.spacing.sm),
        )
    }
}
