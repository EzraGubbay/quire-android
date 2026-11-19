package com.ezragubbay.quire.core.network

/** Non-streaming chat surface of the mobile API. [QuireClient] implements it; repositories depend on this seam. */
interface ChatApi {
    suspend fun recentThreads(limit: Int? = null): ApiResult<List<ThreadDto>>

    suspend fun courseThreads(
        slug: String,
        documentId: String? = null,
    ): ApiResult<List<ThreadDto>>

    suspend fun createThread(
        slug: String,
        title: String? = null,
        documentId: String? = null,
    ): ApiResult<ThreadDto>

    suspend fun deleteThread(
        slug: String,
        threadId: String,
    ): ApiResult<Unit>

    suspend fun messages(
        slug: String,
        threadId: String,
    ): ApiResult<List<ChatMessageDto>>
}
