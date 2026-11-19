package com.ezragubbay.quire.core.network

/** Read-only browse surface of the mobile API. [QuireClient] implements it; repositories depend on this seam. */
interface BrowseApi {
    suspend fun courses(): ApiResult<List<CourseSummaryDto>>

    suspend fun course(slug: String): ApiResult<CourseDetailDto>

    suspend fun sessions(slug: String): ApiResult<List<CourseSessionDto>>

    suspend fun files(slug: String): ApiResult<List<CourseFileDto>>

    suspend fun notes(slug: String): ApiResult<List<NoteDto>>

    suspend fun note(
        slug: String,
        noteId: String,
    ): ApiResult<NoteDetailDto>
}
