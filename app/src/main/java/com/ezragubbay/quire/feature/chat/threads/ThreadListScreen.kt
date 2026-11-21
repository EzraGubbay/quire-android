package com.ezragubbay.quire.feature.chat.threads

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.designsystem.component.EmptyState
import com.ezragubbay.quire.core.designsystem.component.ErrorState
import com.ezragubbay.quire.core.designsystem.component.LoadingSkeleton
import com.ezragubbay.quire.core.designsystem.component.QuirePullToRefresh
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme
import com.ezragubbay.quire.core.network.ThreadDto

/**
 * A thread list (global or per-course) with loading / empty / error / offline states, pull-to-refresh and a
 * "New chat" action. Tapping a row or finishing "New chat" reports through [onOpenThread].
 */
@Composable
fun ThreadListScreen(
    viewModel: ThreadListViewModel,
    screenTag: String,
    onOpenThread: (courseSlug: String, threadId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.onStart() }
    }
    val snackbar = remember { SnackbarHostState() }
    val createFailed = stringResource(R.string.chats_create_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ThreadsEvent.OpenConversation -> onOpenThread(event.courseSlug, event.threadId)
                ThreadsEvent.CreateFailed -> snackbar.showSnackbar(createFailed)
            }
        }
    }
    ThreadListContent(
        state = state,
        screenTag = screenTag,
        snackbar = snackbar,
        onOpenThread = onOpenThread,
        onNewThread = viewModel::newThread,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
    )
}

@Composable
internal fun ThreadListContent(
    state: ThreadsUiState,
    screenTag: String,
    snackbar: SnackbarHostState,
    onOpenThread: (courseSlug: String, threadId: String) -> Unit,
    onNewThread: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize().testTag(screenTag)) {
        when (state) {
            ThreadsUiState.Loading -> Box(Modifier.testTag("threads-skeleton")) { LoadingSkeleton() }
            ThreadsUiState.Empty ->
                Box(Modifier.fillMaxSize().testTag("threads-empty")) {
                    EmptyState(
                        title = stringResource(R.string.chats_empty_title),
                        message = stringResource(R.string.chats_empty_message),
                    )
                }
            ThreadsUiState.Error ->
                Box(Modifier.fillMaxSize().testTag("threads-error")) {
                    ErrorState(
                        message = stringResource(R.string.chats_error),
                        onRetry = onRetry,
                        retryTag = "action-retry",
                    )
                }
            is ThreadsUiState.Content -> Threads(state, onOpenThread, onRefresh)
        }
        ExtendedFloatingActionButton(
            onClick = onNewThread,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.chats_new)) },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(QuireTheme.spacing.md)
                    .testTag("action-new-thread"),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Threads(
    state: ThreadsUiState.Content,
    onOpenThread: (String, String) -> Unit,
    onRefresh: () -> Unit,
) {
    val spacing = QuireTheme.spacing
    Column(Modifier.fillMaxSize()) {
        if (state.offline) OfflineBanner()
        QuirePullToRefresh(isRefreshing = state.refreshing, onRefresh = onRefresh) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("thread-list"),
                // Bottom padding keeps the last row clear of the New chat button.
                contentPadding = PaddingValues(start = spacing.md, end = spacing.md, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(state.threads, key = { it.id }) { thread ->
                    Box(Modifier.testTag("thread-row-${thread.id}")) { ThreadRow(thread, onOpenThread) }
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(
    thread: ThreadDto,
    onOpenThread: (String, String) -> Unit,
) {
    val spacing = QuireTheme.spacing
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { onOpenThread(thread.courseSlug, thread.id) },
    ) {
        Row(
            Modifier.padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(Modifier.weight(1f)) {
                Text(thread.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    thread.courseSlug,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (thread.documentId != null) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.chats_document_badge)) },
                    modifier = Modifier.testTag("thread-doc-badge"),
                )
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("chat-offline-banner"),
    ) {
        Text(
            stringResource(R.string.chats_offline),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = QuireTheme.spacing.md, vertical = QuireTheme.spacing.sm),
        )
    }
}
