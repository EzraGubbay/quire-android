package com.ezragubbay.quire.sync

import com.ezragubbay.quire.core.data.course.CourseRepository
import com.ezragubbay.quire.core.data.course.SyncState
import com.ezragubbay.quire.core.database.CourseDao
import com.ezragubbay.quire.core.network.ApiResult

/** The repository operations a metadata sync needs; a seam so the worker is testable with fakes. */
interface CourseSyncSource {
    /** Refetches the course list (always hits the network). */
    suspend fun refreshCourses(): SyncState

    /** Slugs of the cached courses, after [refreshCourses]. */
    suspend fun courseSlugs(): List<String>

    /** Refetches one course: detail, then sessions, files and notes. */
    suspend fun refreshCourse(slug: String): SyncState
}

class RepositoryCourseSyncSource(
    private val courses: CourseRepository,
    private val dao: CourseDao,
) : CourseSyncSource {
    override suspend fun refreshCourses(): SyncState = courses.refreshCourses(force = true)

    override suspend fun courseSlugs(): List<String> = dao.getAll().map { it.slug }

    override suspend fun refreshCourse(slug: String): SyncState = courses.refreshCourse(slug, force = true)
}

sealed interface SyncOutcome {
    /** Everything refreshed; the last-success time was stamped. */
    data object Success : SyncOutcome

    /** Nothing to do (signed out). */
    data object Skipped : SyncOutcome

    /** A `401`: the session is gone (the network layer already raised session-expired). Never retried. */
    data object Unauthorized : SyncOutcome

    /** [failed] scopes hit a transient failure (offline, 5xx, rate limit); worth a backed-off retry. */
    data class Partial(
        val failed: Int,
    ) : SyncOutcome
}

/**
 * One metadata sync pass: course list, then every course (detail, sessions, files, notes) through the repositories.
 * A `401` stops the pass at once, so the network layer signals session-expiry exactly once. Other failures are
 * counted but do not stop the remaining courses. Only a fully successful pass stamps the last-success time.
 */
class SyncRunner(
    private val source: CourseSyncSource,
    private val store: SyncSettingsStore,
    private val isSignedIn: suspend () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(): SyncOutcome =
        if (isSignedIn()) {
            classify(source.refreshCourses()) ?: syncCourses().also { if (it == SyncOutcome.Success) stamp() }
        } else {
            SyncOutcome.Skipped
        }

    private suspend fun syncCourses(): SyncOutcome {
        var failed = 0
        for (slug in source.courseSlugs()) {
            when (val outcome = classify(source.refreshCourse(slug))) {
                null -> Unit
                SyncOutcome.Unauthorized -> return outcome
                else -> failed++
            }
        }
        return if (failed > 0) SyncOutcome.Partial(failed) else SyncOutcome.Success
    }

    private suspend fun stamp() = store.recordSuccess(clock())

    /** Null = fine (a vanished course or a rejected request is not worth retrying). */
    private fun classify(state: SyncState): SyncOutcome? =
        when ((state as? SyncState.Failed)?.failure) {
            null, is ApiResult.Failure.NotFound, is ApiResult.Failure.InvalidRequest -> null
            is ApiResult.Failure.Unauthorized -> SyncOutcome.Unauthorized
            else -> SyncOutcome.Partial(1)
        }
}
