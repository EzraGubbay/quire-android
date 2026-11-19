package com.ezragubbay.quire.core.data.chat

import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.ChatApi
import com.ezragubbay.quire.core.network.ChatEvent
import com.ezragubbay.quire.core.network.ChatMessageDto
import com.ezragubbay.quire.core.network.ChatStreamApi
import com.ezragubbay.quire.core.network.ChatStreamer
import com.ezragubbay.quire.core.network.PostMessageRequest
import com.ezragubbay.quire.core.network.ThreadDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private val thread = ThreadDto("t1", "sig", "New chat", null, "2026-11-03T10:00:00Z", "2026-11-03T10:00:00Z")

private class FakeChatApi : ChatApi {
    val calls = mutableListOf<String>()

    override suspend fun recentThreads(limit: Int?): ApiResult<List<ThreadDto>> {
        calls += "recent:$limit"
        return ApiResult.Success(listOf(thread))
    }

    override suspend fun courseThreads(
        slug: String,
        documentId: String?,
    ): ApiResult<List<ThreadDto>> {
        calls += "course:$slug:$documentId"
        return ApiResult.Success(listOf(thread))
    }

    override suspend fun createThread(
        slug: String,
        title: String?,
        documentId: String?,
    ): ApiResult<ThreadDto> {
        calls += "create:$slug:$title:$documentId"
        return ApiResult.Success(thread)
    }

    override suspend fun deleteThread(
        slug: String,
        threadId: String,
    ): ApiResult<Unit> {
        calls += "delete:$slug:$threadId"
        return ApiResult.Success(Unit)
    }

    override suspend fun messages(
        slug: String,
        threadId: String,
    ): ApiResult<List<ChatMessageDto>> {
        calls += "messages:$slug:$threadId"
        return ApiResult.Success(emptyList())
    }
}

private class FakeStreamApi(
    private val body: String,
) : ChatStreamApi {
    var last: Triple<String, String, String>? = null

    override suspend fun ask(
        courseSlug: String,
        threadId: String,
        body: PostMessageRequest,
    ): Response<okhttp3.ResponseBody> {
        last = Triple(courseSlug, threadId, body.question)
        return Response.success(this.body.toResponseBody("application/x-ndjson".toMediaType()))
    }
}

class ChatRepositoryTest {
    private val api = FakeChatApi()

    private fun repo(stream: String = "") = ChatRepository(api, ChatStreamer(FakeStreamApi(stream)))

    @Test
    fun threadReadsDelegateWithArguments() =
        runBlocking {
            val r = repo()
            assertEquals(listOf(thread), (r.observeRecentThreads(5).first() as ApiResult.Success).value)
            r.observeCourseThreads("sig", "doc1").first()
            r.createThread("sig", "T", "doc1")
            r.observeMessages("sig", "t1").first()
            r.deleteThread("sig", "t1")
            assertEquals(
                listOf("recent:5", "course:sig:doc1", "create:sig:T:doc1", "messages:sig:t1", "delete:sig:t1"),
                api.calls,
            )
        }

    @Test
    fun observeIsColdAndRefetchesPerCollection() =
        runBlocking {
            val r = repo()
            val flow = r.observeCourseThreads("sig")
            flow.first()
            flow.first()
            assertEquals(2, api.calls.size)
        }

    @Test
    fun sendMessageStreamsEventsFromBody() =
        runBlocking {
            val lines =
                listOf(
                    """{"type":"delta","text":"hi"}""",
                    """{"type":"error","code":"rate_limited","message":"m","kind":"budget"}""",
                )
            val stream = FakeStreamApi(lines.joinToString("\n", postfix = "\n"))
            val events = ChatRepository(api, ChatStreamer(stream)).sendMessage("sig", "t1", "q?").toList()
            assertEquals(Triple("sig", "t1", "q?"), stream.last)
            assertEquals(ChatEvent.Delta("hi"), events[0])
            assertTrue(!(events[1] as ChatEvent.Error).retryable)
        }
}
