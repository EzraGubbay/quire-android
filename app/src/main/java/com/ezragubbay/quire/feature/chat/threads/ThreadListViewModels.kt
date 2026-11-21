package com.ezragubbay.quire.feature.chat.threads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.core.data.chat.ThreadRepository
import com.ezragubbay.quire.core.data.course.Loaded
import com.ezragubbay.quire.core.data.course.SyncState
import com.ezragubbay.quire.core.data.course.showsError
import com.ezragubbay.quire.core.data.course.showsLoading
import com.ezragubbay.quire.core.data.course.showsOfflineBanner
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.ThreadDto
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** What the thread lists need from the data layer (a seam so the ViewModels are testable without Room). */
interface ThreadListSource {
    fun observeRecent(): Flow<Loaded<List<ThreadDto>>>

    fun observeCourse(slug: String): Flow<Loaded<List<ThreadDto>>>

    suspend fun refreshRecent(): SyncState

    suspend fun refreshCourse(slug: String): SyncState

    suspend fun createThread(slug: String): ApiResult<ThreadDto>

    /** Course a new chat from the global tab is created in, or null when no course is known yet. */
    suspend fun defaultCourseSlug(): String?
}

class RepositoryThreadListSource
    @Inject
    constructor(
        private val repository: ThreadRepository,
    ) : ThreadListSource {
        override fun observeRecent() = repository.observeRecent()

        override fun observeCourse(slug: String) = repository.observeCourse(slug)

        override suspend fun refreshRecent() = repository.refreshRecent()

        override suspend fun refreshCourse(slug: String) = repository.refreshCourse(slug)

        override suspend fun createThread(slug: String) = repository.createThread(slug)

        override suspend fun defaultCourseSlug() = repository.defaultCourseSlug()
    }

@Module
@InstallIn(SingletonComponent::class)
internal interface ThreadListSourceModule {
    @Binds
    fun threadListSource(impl: RepositoryThreadListSource): ThreadListSource
}

sealed interface ThreadsUiState {
    /** First load: nothing cached yet and the refresh is running. */
    data object Loading : ThreadsUiState

    /** Refreshed fine and there are no threads. */
    data object Empty : ThreadsUiState

    /** Refresh failed and there is no cache to show. */
    data object Error : ThreadsUiState

    data class Content(
        val threads: List<ThreadDto>,
        /** Network is down but cached threads are shown (read-only). */
        val offline: Boolean,
        val refreshing: Boolean,
    ) : ThreadsUiState
}

internal fun Loaded<List<ThreadDto>>.toUiState(): ThreadsUiState =
    when {
        showsLoading -> ThreadsUiState.Loading
        showsError -> ThreadsUiState.Error
        data.isEmpty() -> ThreadsUiState.Empty
        else ->
            ThreadsUiState.Content(
                // The cache already orders newest first; sort again so a source can't break the contract.
                threads = data.sortedByDescending { it.updatedAt },
                offline = showsOfflineBanner,
                refreshing = state == SyncState.Refreshing,
            )
    }

/** One-shot results of "New chat". */
sealed interface ThreadsEvent {
    data class OpenConversation(
        val courseSlug: String,
        val threadId: String,
    ) : ThreadsEvent

    /** The thread could not be created (offline, server error, or no course to create it in). */
    data object CreateFailed : ThreadsEvent
}

/** Shared behaviour of the global and per-course lists. */
abstract class ThreadListViewModel(
    protected val source: ThreadListSource,
    observe: Flow<Loaded<List<ThreadDto>>>,
) : ViewModel() {
    val state: StateFlow<ThreadsUiState> =
        observe
            .map { it.toUiState() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), ThreadsUiState.Loading)

    private val eventChannel = Channel<ThreadsEvent>(Channel.BUFFERED)
    val events: Flow<ThreadsEvent> = eventChannel.receiveAsFlow()

    private var started = false

    protected abstract suspend fun refreshNow()

    protected abstract suspend fun newThreadCourse(): String?

    /**
     * The screen entered STARTED. The first start is covered by observing (which refreshes); later ones (relaunch,
     * returning from a conversation) refetch so a stale cache shows its offline state honestly.
     */
    fun onStart() {
        if (started) refresh() else started = true
    }

    /** Pull-to-refresh. */
    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    fun retry() = refresh()

    /** Creates a thread (course picker is a later refinement) and reports where to navigate. */
    fun newThread() {
        viewModelScope.launch {
            val slug = newThreadCourse()
            val created = slug?.let { source.createThread(it) }
            eventChannel.send(
                if (created is ApiResult.Success) {
                    ThreadsEvent.OpenConversation(created.value.courseSlug, created.value.id)
                } else {
                    ThreadsEvent.CreateFailed
                },
            )
        }
    }
}

/** The global Chats tab: recent threads across all courses. */
@HiltViewModel
class ChatsViewModel
    @Inject
    constructor(
        source: ThreadListSource,
    ) : ThreadListViewModel(source, source.observeRecent()) {
        override suspend fun refreshNow() {
            source.refreshRecent()
        }

        override suspend fun newThreadCourse() = source.defaultCourseSlug()
    }

/** One course's threads, behind the course screen's Ask entry point. */
@HiltViewModel
class CourseThreadsViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        source: ThreadListSource,
    ) : ThreadListViewModel(source, source.observeCourse(checkNotNull(savedState["slug"]))) {
        // Type-safe navigation puts the route's `slug` argument in the SavedStateHandle.
        private val slug: String = checkNotNull(savedState["slug"])

        override suspend fun refreshNow() {
            source.refreshCourse(slug)
        }

        override suspend fun newThreadCourse() = slug
    }
