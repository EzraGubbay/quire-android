package com.ezragubbay.quire.push

import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.BrowseApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** A file's display name and server type label. */
data class FileName(
    val title: String,
    val type: String,
)

/** Resolves the names a notification shows. Each lookup may be slow or fail; null means "unknown". */
interface PushNamesLookup {
    suspend fun courseName(courseSlug: String): String?

    suspend fun file(
        courseSlug: String,
        fileId: String,
    ): FileName?

    suspend fun noteTitle(
        courseSlug: String,
        noteId: String,
    ): String?
}

/** Room cache first, then the API with a per-call timeout (ADR 0003 §2). */
class CachedPushNamesLookup(
    private val db: QuireDatabase,
    private val browse: BrowseApi,
    private val push: PushClient,
    private val apiTimeoutMillis: Long = API_TIMEOUT_MS,
) : PushNamesLookup {
    override suspend fun courseName(courseSlug: String): String? =
        db
            .courseDao()
            .getAll()
            .firstOrNull { it.slug == courseSlug }
            ?.name
            ?: api { browse.course(courseSlug) }?.name

    override suspend fun file(
        courseSlug: String,
        fileId: String,
    ): FileName? =
        db
            .fileDao()
            .getForCourse(courseSlug)
            .firstOrNull { it.fileId == fileId }
            ?.let { FileName(it.title, it.type) }
            ?: api { push.file(courseSlug, fileId) }?.let { FileName(it.title, it.type) }

    override suspend fun noteTitle(
        courseSlug: String,
        noteId: String,
    ): String? =
        db
            .noteDao()
            .observe(courseSlug)
            .first()
            .firstOrNull { it.id == noteId }
            ?.title
            ?: api { browse.note(courseSlug, noteId) }?.title

    private suspend fun <T> api(call: suspend () -> ApiResult<T>): T? =
        (withTimeoutOrNull(apiTimeoutMillis) { call() } as? ApiResult.Success)?.value

    companion object {
        const val API_TIMEOUT_MS = 5_000L
    }
}
