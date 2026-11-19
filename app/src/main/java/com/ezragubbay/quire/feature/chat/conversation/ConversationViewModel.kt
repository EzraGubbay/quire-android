package com.ezragubbay.quire.feature.chat.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.core.data.chat.ChatRepository
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.ChatEvent
import com.ezragubbay.quire.core.network.ChatMessageDto
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Role { User, Assistant }

/** One chat bubble. [stopped] marks an answer the user cut short (partial text kept). */
data class Bubble(
    val role: Role,
    val text: String,
    val stopped: Boolean = false,
)

/**
 * [history] bubbles come first, then [live] ones sent in this session; the bubble index (test-tag suffix) is the
 * position in [bubbles].
 */
data class ConversationUiState(
    val history: List<Bubble> = emptyList(),
    val live: List<Bubble> = emptyList(),
    val historyLoaded: Boolean = false,
    val historyFailed: Boolean = false,
    val streaming: Boolean = false,
    val error: String? = null,
) {
    val bubbles: List<Bubble> get() = history + live
}

/** What the conversation needs from the chat data layer (a seam so the ViewModel is testable without HTTP). */
interface ConversationSource {
    fun observeMessages(
        slug: String,
        threadId: String,
    ): Flow<ApiResult<List<ChatMessageDto>>>

    fun sendMessage(
        slug: String,
        threadId: String,
        question: String,
    ): Flow<ChatEvent>
}

class RepositoryConversationSource
    @Inject
    constructor(
        private val repository: ChatRepository,
    ) : ConversationSource {
        override fun observeMessages(
            slug: String,
            threadId: String,
        ) = repository.observeMessages(slug, threadId)

        override fun sendMessage(
            slug: String,
            threadId: String,
            question: String,
        ) = repository.sendMessage(slug, threadId, question)
    }

@Module
@InstallIn(SingletonComponent::class)
internal interface ConversationSourceModule {
    @Binds
    fun conversationSource(impl: RepositoryConversationSource): ConversationSource
}

@HiltViewModel
class ConversationViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val source: ConversationSource,
    ) : ViewModel() {
        private val slug: String = checkNotNull(savedState["courseSlug"])
        private val threadId: String = checkNotNull(savedState["threadId"])

        private val _state = MutableStateFlow(ConversationUiState())
        val state: StateFlow<ConversationUiState> = _state.asStateFlow()

        private var streamJob: Job? = null

        init {
            loadHistory()
        }

        fun retryHistory() = loadHistory()

        private fun loadHistory() {
            _state.update { it.copy(historyFailed = false) }
            viewModelScope.launch {
                source.observeMessages(slug, threadId).collect { result ->
                    _state.update {
                        when (result) {
                            is ApiResult.Success ->
                                it.copy(
                                    history = result.value.map { m -> m.toBubble() },
                                    historyLoaded = true,
                                    historyFailed = false,
                                )
                            is ApiResult.Failure -> it.copy(historyLoaded = true, historyFailed = true)
                        }
                    }
                }
            }
        }

        /** Sends [question]: user bubble now, assistant bubble on the first delta, grown in place until done. */
        fun send(question: String) {
            val text = question.trim()
            if (text.isEmpty() || _state.value.streaming) return
            _state.update {
                it.copy(live = it.live + Bubble(Role.User, text), streaming = true, error = null)
            }
            streamJob =
                viewModelScope.launch {
                    source.sendMessage(slug, threadId, text).collect(::onEvent)
                }
        }

        /** Cancels the in-flight stream, keeping the accumulated partial answer (marked stopped). */
        fun stop() {
            streamJob?.cancel()
            streamJob = null
            _state.update { s ->
                val last = s.live.lastOrNull()
                val live =
                    if (s.streaming && last?.role == Role.Assistant) {
                        s.live.dropLast(1) + last.copy(stopped = true)
                    } else {
                        s.live
                    }
                s.copy(live = live, streaming = false)
            }
        }

        private fun onEvent(event: ChatEvent) {
            when (event) {
                is ChatEvent.User -> Unit
                is ChatEvent.Delta -> _state.update { it.copy(live = it.live.appendDelta(event.text)) }
                is ChatEvent.Done ->
                    _state.update {
                        it.copy(live = it.live.settle(event.message.content), streaming = false)
                    }
                is ChatEvent.Error -> _state.update { it.copy(streaming = false, error = event.message) }
            }
        }

        override fun onCleared() {
            streamJob?.cancel()
        }
    }

private fun ChatMessageDto.toBubble() = Bubble(if (role == "user") Role.User else Role.Assistant, content)

private fun List<Bubble>.appendDelta(text: String): List<Bubble> {
    val last = lastOrNull()
    return if (last?.role ==
        Role.Assistant
    ) {
        dropLast(1) + last.copy(text = last.text + text)
    } else {
        this + Bubble(Role.Assistant, text)
    }
}

private fun List<Bubble>.settle(text: String): List<Bubble> {
    val last = lastOrNull()
    return if (last?.role ==
        Role.Assistant
    ) {
        dropLast(1) + last.copy(text = text)
    } else {
        this + Bubble(Role.Assistant, text)
    }
}
