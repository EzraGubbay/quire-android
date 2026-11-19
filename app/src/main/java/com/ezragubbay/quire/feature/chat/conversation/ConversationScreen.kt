package com.ezragubbay.quire.feature.chat.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.designsystem.component.ErrorState
import com.ezragubbay.quire.feature.notes.BlockView
import com.ezragubbay.quire.feature.notes.NoteBlock
import com.ezragubbay.quire.feature.notes.rememberMarkwon
import com.ezragubbay.quire.feature.notes.segmentMarkdown
import kotlinx.coroutines.launch

const val CONVERSATION_TAG = "screen-conversation"
const val CHAT_INPUT_TAG = "chat-input"
const val ACTION_SEND_TAG = "action-send"
const val ACTION_STOP_TAG = "action-stop"
const val ANSWER_MARKDOWN_TAG = "answer-markdown"
const val ANSWER_MATH_TAG = "answer-math"
const val CHAT_ERROR_TAG = "chat-error"
const val ACTION_RETRY_TAG = "action-retry"
const val CHAT_OFFLINE_BANNER_TAG = "chat-offline-banner"

/** The chat conversation: history, then live streaming answers with Stop; answers render Markdown + block math. */
@Composable
fun ConversationScreen(
    onBack: () -> Unit,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConversationContent(state, onBack, viewModel::send, viewModel::stop, viewModel::retryHistory, viewModel::retry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationContent(
    state: ConversationUiState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRetryHistory: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag(CONVERSATION_TAG).imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conversation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("conversation-back")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.conversation_back),
                        )
                    }
                },
            )
        },
        bottomBar = { Composer(state.streaming, !state.offline, onSend, onStop) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.offline) OfflineBanner()
            if (state.historyFailed) {
                ErrorState(stringResource(R.string.conversation_history_error), onRetry = onRetryHistory)
            } else {
                MessageList(state, onRetry, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MessageList(
    state: ConversationUiState,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val bubbles = state.bubbles
    val listState = rememberLazyListState()
    val atBottom by remember { derivedAtBottom(listState) }
    val lastLength = bubbles.lastOrNull()?.text?.length ?: 0
    var followTail by remember { mutableStateOf(true) }
    followTail = atBottom || (followTail && listState.isScrollInProgress.not())
    LaunchedEffect(bubbles.size, lastLength) {
        if (followTail && bubbles.isNotEmpty()) listState.scrollToItem(bubbles.lastIndex)
    }
    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(bubbles, key = { index, _ -> index }) { index, bubble ->
                BubbleView(index, bubble)
            }
            if (state.error != null) {
                item(key = "error") {
                    ChatErrorRow(state.error, onRetry)
                }
            }
        }
        if (!atBottom && bubbles.isNotEmpty()) {
            val scope = rememberCoroutineScope()
            AssistChip(
                onClick = { scope.launch { listState.animateScrollToItem(bubbles.lastIndex) } },
                label = { Text(stringResource(R.string.conversation_jump_latest)) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).testTag("action-jump-latest"),
            )
        }
    }
}

@Composable
private fun ChatErrorRow(
    failure: ChatFailure,
    onRetry: () -> Unit,
) {
    Column {
        Text(
            text = stringResource(failure.messageRes()),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(CHAT_ERROR_TAG),
        )
        if (failure.retryable) {
            TextButton(onClick = onRetry, modifier = Modifier.testTag(ACTION_RETRY_TAG)) {
                Text(stringResource(R.string.conversation_retry))
            }
        }
    }
}

private fun ChatFailure.messageRes(): Int =
    when (this) {
        ChatFailure.Budget -> R.string.conversation_error_budget
        ChatFailure.AiUnavailable -> R.string.conversation_error_ai_unavailable
        ChatFailure.Network -> R.string.conversation_error_network
        ChatFailure.Provider -> R.string.conversation_error_provider
    }

@Composable
private fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().testTag(CHAT_OFFLINE_BANNER_TAG),
    ) {
        Text(
            stringResource(R.string.conversation_offline),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

private fun derivedAtBottom(listState: LazyListState) = derivedStateOf { !listState.canScrollForward }

@Composable
private fun BubbleView(
    index: Int,
    bubble: Bubble,
) {
    val isUser = bubble.role == Role.User
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color =
                if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            modifier =
                Modifier
                    .fillMaxWidth(if (isUser) USER_WIDTH else 1f)
                    .testTag(if (isUser) "message-user-$index" else "message-assistant-$index"),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (isUser) {
                    SelectionContainer { Text(bubble.text, style = MaterialTheme.typography.bodyLarge) }
                } else {
                    AnswerBody(bubble.text)
                    if (bubble.stopped) {
                        Text(
                            stringResource(R.string.conversation_stopped),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.testTag("message-stopped-$index"),
                        )
                    }
                    val clipboard = LocalClipboardManager.current
                    val copy = stringResource(R.string.conversation_copy)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(bubble.text)) }) { Text(copy) }
                }
            }
        }
    }
}

private const val USER_WIDTH = 0.85f

/** Assistant Markdown via the notes reader's Markwon blocks (ADR 0002): prose runs plus `answer-math` equations. */
@Composable
private fun AnswerBody(markdown: String) {
    val markwon = rememberMarkwon()
    val blocks = remember(markdown) { segmentMarkdown(markdown) }
    Column(Modifier.fillMaxWidth().testTag(ANSWER_MARKDOWN_TAG)) {
        blocks.forEach { block ->
            when (block) {
                is NoteBlock.Text -> BlockView(markwon, block.markdown, Modifier.fillMaxWidth())
                is NoteBlock.Math ->
                    BlockView(
                        markwon = markwon,
                        markdown = "$$\n${block.latex}\n$$",
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag(ANSWER_MATH_TAG)
                                .semantics { contentDescription = block.latex },
                        description = block.latex,
                    )
            }
        }
    }
}

@Composable
private fun Composer(
    streaming: Boolean,
    enabled: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.conversation_input_hint)) },
            enabled = enabled,
            maxLines = MAX_INPUT_LINES,
            modifier = Modifier.weight(1f).testTag(CHAT_INPUT_TAG),
        )
        if (streaming) {
            IconButton(onClick = onStop, modifier = Modifier.testTag(ACTION_STOP_TAG)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.conversation_stop))
            }
        }
        IconButton(
            onClick = {
                onSend(text)
                text = ""
            },
            enabled = enabled && text.isNotBlank() && !streaming,
            modifier = Modifier.testTag(ACTION_SEND_TAG),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.conversation_send))
        }
    }
}

private const val MAX_INPUT_LINES = 5
