package com.ezragubbay.quire.core.network

/** [ChatApi] over Retrofit: every call returns an [ApiResult] and never throws. */
class ChatClient(
    private val api: ChatRestApi,
) : ChatApi {
    override suspend fun recentThreads(limit: Int?): ApiResult<List<ThreadDto>> =
        apiCall(requireBody = true) { api.recentThreads(limit) }

    override suspend fun courseThreads(
        slug: String,
        documentId: String?,
    ): ApiResult<List<ThreadDto>> = apiCall(requireBody = true) { api.courseThreads(slug, documentId) }

    override suspend fun createThread(
        slug: String,
        title: String?,
        documentId: String?,
    ): ApiResult<ThreadDto> =
        apiCall(requireBody = true) { api.createThread(slug, CreateThreadRequest(title, documentId)) }

    override suspend fun deleteThread(
        slug: String,
        threadId: String,
    ): ApiResult<Unit> = apiCall { api.deleteThread(slug, threadId) }

    override suspend fun messages(
        slug: String,
        threadId: String,
    ): ApiResult<List<ChatMessageDto>> = apiCall(requireBody = true) { api.messages(slug, threadId) }
}
