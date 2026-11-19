package com.ezragubbay.quire.core.data.chat

import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.ChatApi
import com.ezragubbay.quire.core.network.ChatEvent
import com.ezragubbay.quire.core.network.ChatMessageDto
import com.ezragubbay.quire.core.network.ChatStreamer
import com.ezragubbay.quire.core.network.ThreadDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chat data layer. Thread and history reads are network-only for now (no Room cache; #45/#46 add offline caching):
 * each `observe*` is a cold flow that emits one [ApiResult] per collection, so re-collecting refreshes.
 * Messages live under a course in the API, so history and sending take the course slug as well as the thread id.
 */
@Singleton
class ChatRepository
    @Inject
    constructor(
        private val api: ChatApi,
        private val streamer: ChatStreamer,
    ) {
        fun observeRecentThreads(limit: Int? = null): Flow<ApiResult<List<ThreadDto>>> =
            flow { emit(api.recentThreads(limit)) }

        fun observeCourseThreads(
            slug: String,
            documentId: String? = null,
        ): Flow<ApiResult<List<ThreadDto>>> = flow { emit(api.courseThreads(slug, documentId)) }

        suspend fun createThread(
            slug: String,
            title: String? = null,
            documentId: String? = null,
        ): ApiResult<ThreadDto> = api.createThread(slug, title, documentId)

        suspend fun deleteThread(
            slug: String,
            threadId: String,
        ): ApiResult<Unit> = api.deleteThread(slug, threadId)

        fun observeMessages(
            slug: String,
            threadId: String,
        ): Flow<ApiResult<List<ChatMessageDto>>> = flow { emit(api.messages(slug, threadId)) }

        /**
         * Cold stream of one answer. Stop it by cancelling the collecting coroutine (the HTTP call is dropped; the
         * caller keeps the partial text it accumulated from [ChatEvent.Delta]s). Retry = call again with the same
         * question (the POST carries only `question`).
         */
        fun sendMessage(
            slug: String,
            threadId: String,
            question: String,
        ): Flow<ChatEvent> = streamer.ask(slug, threadId, question)
    }
