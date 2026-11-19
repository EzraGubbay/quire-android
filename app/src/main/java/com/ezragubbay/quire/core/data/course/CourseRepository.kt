package com.ezragubbay.quire.core.data.course

import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.BrowseApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for browse data. UI observes Room-backed [Flow]s (cache served immediately);
 * a network refresh writes through to Room in one transaction per table and removes rows the server dropped.
 * A failed refresh never clears the cache: it only moves [Loaded.state] to [SyncState.Failed].
 *
 * Refresh scopes: the course list (`courses`) and one scope per course (detail + sessions + files + notes).
 * Observing a scope refreshes it if it was never synced or its last success is older than [staleAfterMillis];
 * [refreshCourses] / [refreshCourse] always refetch (pull-to-refresh).
 */
class CourseRepository(
    private val db: QuireDatabase,
    private val api: BrowseApi,
    private val staleAfterMillis: Long = DEFAULT_STALE_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Scope {
        val state = MutableStateFlow<SyncState>(SyncState.Idle)
        val mutex = Mutex()

        @Volatile var lastSuccess: Long? = null
    }

    private val scopes = ConcurrentHashMap<String, Scope>()

    private fun scope(key: String) = scopes.getOrPut(key) { Scope() }

    fun observeCourses(): Flow<Loaded<List<Course>>> =
        observeScope(COURSES, db.courseDao().observeAll().map { rows -> rows.map { it.toModel() } }) {
            refreshCourses(force = false)
        }

    fun observeCourse(slug: String): Flow<Loaded<CourseDetail?>> =
        observeScope(
            courseKey(slug),
            combine(db.courseDao().observe(slug), db.courseDao().observeCounts(slug)) { course, counts ->
                course?.let { CourseDetail(it.toModel(), counts.toModel()) }
            },
        ) { refreshCourse(slug, force = false) }

    fun observeSessions(slug: String): Flow<Loaded<List<CourseSession>>> =
        observeScope(courseKey(slug), db.sessionDao().observe(slug).map { rows -> rows.map { it.toModel() } }) {
            refreshCourse(slug, force = false)
        }

    fun observeFiles(slug: String): Flow<Loaded<List<CourseFile>>> =
        observeScope(courseKey(slug), db.fileDao().observe(slug).map { rows -> rows.map { it.toModel() } }) {
            refreshCourse(slug, force = false)
        }

    fun observeNotes(slug: String): Flow<Loaded<List<CourseNote>>> =
        observeScope(courseKey(slug), db.noteDao().observe(slug).map { rows -> rows.map { it.toModel() } }) {
            refreshCourse(slug, force = false)
        }

    /** Refetches the course list and writes it through. Returns the resulting state. */
    suspend fun refreshCourses(force: Boolean = true): SyncState =
        sync(COURSES, force) {
            api.courses().ifSuccess { list -> db.courseDao().replaceAll(list.map { it.toEntity() }) }
        }

    /** Refetches one course's detail, sessions, files and notes. Detail goes first: children need the course row. */
    suspend fun refreshCourse(
        slug: String,
        force: Boolean = true,
    ): SyncState =
        sync(courseKey(slug), force) {
            api.course(slug).ifSuccess { dto ->
                db.courseDao().upsertWithCounts(dto.toSummary().toEntity(), dto.counts.toEntity(slug))
            } ?: api.sessions(slug).ifSuccess { rows ->
                db.sessionDao().replaceForCourse(slug, rows.map { it.toEntity(slug) })
            } ?: api.files(slug).ifSuccess { rows ->
                db.fileDao().replaceForCourse(slug, rows.map { it.toEntity(slug) })
            } ?: api.notes(slug).ifSuccess { rows ->
                db.noteDao().replaceForCourse(slug, rows.map { it.toEntity(slug) })
            }
        }

    private fun <T> observeScope(
        key: String,
        query: Flow<T>,
        refresh: suspend () -> SyncState,
    ): Flow<Loaded<T>> =
        channelFlow {
            val state = scope(key).state
            launch { refresh() }
            // A scope not yet synced is about to refresh (observing triggers it), so report it as loading.
            combine(query, state) { data, s ->
                Loaded(
                    data,
                    if (s ==
                        SyncState.Idle
                    ) {
                        SyncState.Refreshing
                    } else {
                        s
                    },
                )
            }.collect { send(it) }
        }

    /**
     * Runs [fetch] (null = success, else the failure) under the scope's mutex. Unless [force], skips when the scope
     * was synced within [staleAfterMillis] or is already refreshing.
     */
    private suspend fun sync(
        key: String,
        force: Boolean,
        fetch: suspend () -> ApiResult.Failure?,
    ): SyncState {
        val scope = scope(key)
        return scope.mutex.withLock {
            val fresh = scope.lastSuccess?.let { clock() - it < staleAfterMillis } == true
            if (!force && fresh) return@withLock scope.state.value
            val before = scope.state.value
            scope.state.value = SyncState.Refreshing
            val failure =
                try {
                    fetch()
                } catch (e: CancellationException) {
                    scope.state.value = before
                    throw e
                }
            if (failure == null) scope.lastSuccess = clock()
            (failure?.let { SyncState.Failed(it) } ?: SyncState.Synced).also { scope.state.value = it }
        }
    }

    private companion object {
        const val COURSES = "courses"
        const val DEFAULT_STALE_MS = 5 * 60_000L

        fun courseKey(slug: String) = "course:$slug"
    }
}

/** Runs [onSuccess] with the value and returns null, or returns the failure. */
private suspend inline fun <T> ApiResult<T>.ifSuccess(onSuccess: (T) -> Unit): ApiResult.Failure? =
    when (this) {
        is ApiResult.Success -> {
            onSuccess(value)
            null
        }
        is ApiResult.Failure -> this
    }
