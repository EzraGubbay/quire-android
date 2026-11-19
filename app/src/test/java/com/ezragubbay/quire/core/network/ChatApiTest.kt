package com.ezragubbay.quire.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ChatApi

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client =
            QuireClientFactory.chatApi(
                QuireClientFactory.okHttpClient(
                    FakeServerConfig(server.url("/").toString()),
                    FakeTokenProvider("t"),
                    SessionEvents(),
                    NetworkOptions(allowCleartextLocal = true),
                ),
            )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun recentAndCourseThreadsDecode() =
        runBlocking {
            server.enqueue(fixtureResponse("chat-threads-recent.json"))
            val recent = (client.recentThreads(10) as ApiResult.Success).value
            assertEquals(2, recent.size)
            assertNull(recent[0].documentId)
            assertEquals("6f1c2a52-8b0e-4c6a-9d63-2a3a7c1d9e10", recent[1].documentId)
            assertEquals("/api/mobile/v1/threads?limit=10", server.takeRequest().path)

            server.enqueue(fixtureResponse("chat-threads-document.json"))
            client.courseThreads("sig", "doc1")
            assertEquals("/api/mobile/v1/courses/sig/threads?documentId=doc1", server.takeRequest().path)
        }

    @Test
    fun createThreadPostsBodyAndDecodes201() =
        runBlocking {
            server.enqueue(fixtureResponse("chat-thread-created.json", code = 201))
            val t = (client.createThread("sig", "Hi") as ApiResult.Success).value
            assertEquals("New chat", t.title)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("""{"title":"Hi"}""", req.body.readUtf8())
        }

    @Test
    fun messagesDecodeCitationsWithoutHref() =
        runBlocking {
            server.enqueue(fixtureResponse("chat-messages-history.json"))
            val msgs = (client.messages("sig", "t1") as ApiResult.Success).value
            assertEquals(listOf("user", "assistant"), msgs.map { it.role })
            assertEquals(7, msgs[1].citations.single().pageNo)
            assertNull(msgs[0].error)
            server.enqueue(fixtureResponse("chat-messages-empty.json"))
            assertTrue((client.messages("sig", "t1") as ApiResult.Success).value.isEmpty())
        }

    @Test
    fun deleteThread204IsSuccess() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(204))
            assertTrue(client.deleteThread("sig", "t1") is ApiResult.Success)
        }
}
