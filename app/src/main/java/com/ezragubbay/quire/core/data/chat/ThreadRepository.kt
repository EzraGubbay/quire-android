package com.ezragubbay.quire.core.data.chat

import com.ezragubbay.quire.core.data.course.Loaded
import com.ezragubbay.quire.core.data.course.SyncState
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.database.ThreadEntity
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.ThreadDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first chat thread lists. Room is the source of truth (served immediately); every observation refreshes
 * from [ChatRepository] and writes through. A failed refresh never clears the cache, it only moves
 * [Loaded.state] to [SyncState.Failed] (the same contract as `CourseRepository`).
 * Scopes: the global recent list and one list per course.
 */
@Singleton
class ThreadRepository
    @Inject
    constructor(
        private val db: QuireDatabase,
        private val chat: ChatRepository,
    ) {
        private val states = ConcurrentHashMap<String, MutableStateFlow<SyncState>>()

        fun observeRecent(): Flow<Loaded<List<ThreadDto>>> =
            observeScope(RECENT, db.threadDao().observeRecent()) { refreshRecent() }

        fun observeCourse(slug: String): Flow<Loaded<List<ThreadDto>>> =
            observeScope(courseKey(slug), db.threadDao().observeCourse(slug)) { refreshCourse(slug) }

        suspend fun refreshRecent(): SyncState =
            sync(
                RECENT,
                { chat.observeRecentThreads().first() },
            ) { db.threadDao().replaceAll(it.map(ThreadDto::toEntity)) }

        suspend fun refreshCourse(slug: String): SyncState =
            sync(courseKey(slug), { chat.observeCourseThreads(slug).first() }) {
                db.threadDao().replaceForCourse(slug, it.map(ThreadDto::toEntity))
            }

        /** Creates a thread on the server and caches it so it shows in the lists straight away. */
        suspend fun createThread(slug: String): ApiResult<ThreadDto> =
            chat.createThread(slug).also { if (it is ApiResult.Success) db.threadDao().upsert(it.value.toEntity()) }

        /** The course a "New chat" from the global tab goes to: the latest thread's course, else the first course. */
        suspend fun defaultCourseSlug(): String? =
            db.threadDao().latest()?.courseSlug ?: db
                .courseDao()
                .getAll()
                .firstOrNull()
                ?.slug

        private fun observeScope(
            key: String,
            query: Flow<List<ThreadEntity>>,
            refresh: suspend () -> SyncState,
        ): Flow<Loaded<List<ThreadDto>>> =
            channelFlow {
                launch { refresh() }
                // Not yet synced means the refresh launched above is about to run, so report it as loading.
                combine(query.map { rows -> rows.map(ThreadEntity::toDto) }, states.state(key)) { data, s ->
                    Loaded(data, if (s == SyncState.Idle) SyncState.Refreshing else s)
                }.collect { send(it) }
            }

        private suspend fun sync(
            key: String,
            fetch: suspend () -> ApiResult<List<ThreadDto>>,
            write: suspend (List<ThreadDto>) -> Unit,
        ): SyncState {
            val state = states.state(key)
            val before = state.value
            state.value = SyncState.Refreshing
            return try {
                when (val result = fetch()) {
                    is ApiResult.Success -> {
                        write(result.value)
                        SyncState.Synced
                    }
                    is ApiResult.Failure -> SyncState.Failed(result)
                }.also { state.value = it }
            } catch (e: CancellationException) {
                state.value = before
                throw e
            }
        }
    }

private const val RECENT = "recent"

private fun courseKey(slug: String) = "course:$slug"

private fun ConcurrentHashMap<String, MutableStateFlow<SyncState>>.state(key: String) =
    getOrPut(key) { MutableStateFlow(SyncState.Idle) }

private fun ThreadDto.toEntity() = ThreadEntity(id, courseSlug, title, documentId, createdAt, updatedAt)

private fun ThreadEntity.toDto() = ThreadDto(id, courseSlug, title, documentId, createdAt, updatedAt)
