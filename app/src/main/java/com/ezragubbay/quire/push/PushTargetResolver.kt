package com.ezragubbay.quire.push

import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.BrowseApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** What a tapped target pointed at that no longer exists; the shell shows a snackbar for it. */
enum class PushTargetMissing { Course, File, Note }

/** Where to navigate for a tap, and what was [missing] when the original target was stale. */
data class PushTargetResolution(
    val target: PushTarget,
    val missing: PushTargetMissing? = null,
)

/** Checks a tapped target before the shell navigates to it (ADR 0003 §4.5). Never throws. */
fun interface PushTargetResolver {
    suspend fun resolve(target: PushTarget): PushTargetResolution

    companion object {
        /** Navigates straight to the target without checking it. */
        val Unchecked = PushTargetResolver { PushTargetResolution(it) }
    }
}

/**
 * Asks the server whether the course and the file / note still exist, in parallel and within [timeoutMillis] in
 * total. Only a `404` changes the destination: a missing file or note opens its course, a missing course opens
 * Courses. Offline, a timeout or any other failure navigates anyway (cached or downloaded content may still open).
 */
class ApiPushTargetResolver(
    private val browse: BrowseApi,
    private val push: PushClient,
    private val timeoutMillis: Long = TIMEOUT_MS,
) : PushTargetResolver {
    override suspend fun resolve(target: PushTarget): PushTargetResolution {
        val slug =
            when (target) {
                PushTarget.Home -> return PushTargetResolution(target)
                is PushTarget.Course -> target.courseSlug
                is PushTarget.File -> target.courseSlug
                is PushTarget.Note -> target.courseSlug
            }
        return withTimeoutOrNull(timeoutMillis) { check(target, slug) } ?: PushTargetResolution(target)
    }

    private suspend fun check(
        target: PushTarget,
        slug: String,
    ): PushTargetResolution =
        coroutineScope {
            val course = async { browse.course(slug) }
            val item =
                async {
                    when (target) {
                        is PushTarget.File -> push.file(slug, target.fileId)
                        is PushTarget.Note -> browse.note(slug, target.noteId)
                        else -> null
                    }
                }
            when {
                course.await() is ApiResult.Failure.NotFound ->
                    PushTargetResolution(PushTarget.Home, PushTargetMissing.Course)
                item.await() !is ApiResult.Failure.NotFound -> PushTargetResolution(target)
                target is PushTarget.Note -> PushTargetResolution(PushTarget.Course(slug), PushTargetMissing.Note)
                else -> PushTargetResolution(PushTarget.Course(slug), PushTargetMissing.File)
            }
        }

    companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
