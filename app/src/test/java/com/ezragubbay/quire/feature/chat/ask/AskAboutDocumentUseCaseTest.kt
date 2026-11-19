package com.ezragubbay.quire.feature.chat.ask

import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.ThreadDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AskAboutDocumentUseCaseTest {
    private class FakeSource(
        var existing: ApiResult<List<ThreadDto>> = ApiResult.Success(emptyList()),
        var created: ApiResult<ThreadDto> = ApiResult.Success(thread("new")),
    ) : DocumentThreadSource {
        val createCalls = mutableListOf<Pair<String, String>>()

        override suspend fun documentThreads(
            slug: String,
            documentId: String,
        ) = existing

        override suspend fun createDocumentThread(
            slug: String,
            documentId: String,
        ): ApiResult<ThreadDto> {
            createCalls += slug to documentId
            return created
        }
    }

    @Test
    fun existingDocumentThread_isReused_withoutCreating() =
        runTest {
            val source = FakeSource(existing = ApiResult.Success(listOf(thread("t1", "2026-01-01"))))
            val result = AskAboutDocumentUseCase(source)("c", "d")
            assertEquals("t1", (result as ApiResult.Success).value.id)
            assertTrue(source.createCalls.isEmpty())
        }

    @Test
    fun severalDocumentThreads_mostRecentIsReused() =
        runTest {
            val source =
                FakeSource(
                    existing =
                        ApiResult.Success(
                            listOf(
                                thread("old", "2026-01-01"),
                                thread("newest", "2026-03-01"),
                                thread("mid", "2026-02-01"),
                            ),
                        ),
                )
            val result = AskAboutDocumentUseCase(source)("c", "d")
            assertEquals("newest", (result as ApiResult.Success).value.id)
        }

    @Test
    fun noDocumentThread_createsOneScopedToTheDocument() =
        runTest {
            val source = FakeSource()
            val result = AskAboutDocumentUseCase(source)("c", "d")
            assertEquals("new", (result as ApiResult.Success).value.id)
            assertEquals(listOf("c" to "d"), source.createCalls)
        }

    @Test
    fun lookupFailure_isReturned_andNothingIsCreated() =
        runTest {
            val failure = ApiResult.Failure.Unauthorized("no")
            val source = FakeSource(existing = failure)
            val result = AskAboutDocumentUseCase(source)("c", "d")
            assertEquals(failure, result)
            assertTrue(source.createCalls.isEmpty())
        }

    @Test
    fun createFailure_isReturned() =
        runTest {
            val failure = ApiResult.Failure.Unauthorized("no")
            val result = AskAboutDocumentUseCase(FakeSource(created = failure))("c", "d")
            assertEquals(failure, result)
        }

    companion object {
        fun thread(
            id: String,
            updatedAt: String = "2026-01-01",
        ) = ThreadDto(id, "c", "t", "d", updatedAt, updatedAt)
    }
}
