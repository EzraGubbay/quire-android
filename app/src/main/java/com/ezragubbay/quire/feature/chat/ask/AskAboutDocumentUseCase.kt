package com.ezragubbay.quire.feature.chat.ask

import com.ezragubbay.quire.core.data.chat.ChatRepository
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.ThreadDto
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** What 'Ask about this document' needs from the chat data layer (a seam so the use case is testable without HTTP). */
interface DocumentThreadSource {
    suspend fun documentThreads(
        slug: String,
        documentId: String,
    ): ApiResult<List<ThreadDto>>

    suspend fun createDocumentThread(
        slug: String,
        documentId: String,
    ): ApiResult<ThreadDto>
}

class RepositoryDocumentThreadSource
    @Inject
    constructor(
        private val repository: ChatRepository,
    ) : DocumentThreadSource {
        override suspend fun documentThreads(
            slug: String,
            documentId: String,
        ) = repository.observeCourseThreads(slug, documentId).first()

        override suspend fun createDocumentThread(
            slug: String,
            documentId: String,
        ) = repository.createThread(slug, documentId = documentId)
    }

@Module
@InstallIn(SingletonComponent::class)
internal interface DocumentThreadSourceModule {
    @Binds
    fun documentThreadSource(impl: RepositoryDocumentThreadSource): DocumentThreadSource
}

/**
 * Resolves the thread for 'Ask about this document': reuses the most recent thread already scoped to
 * [documentId] (no duplicates), and creates a document-scoped one only when there is none.
 */
class AskAboutDocumentUseCase
    @Inject
    constructor(
        private val source: DocumentThreadSource,
    ) {
        suspend operator fun invoke(
            slug: String,
            documentId: String,
        ): ApiResult<ThreadDto> =
            when (val existing = source.documentThreads(slug, documentId)) {
                is ApiResult.Failure -> existing
                is ApiResult.Success ->
                    existing.value
                        .filter { it.documentId == documentId }
                        .maxByOrNull { it.updatedAt }
                        ?.let { ApiResult.Success(it) }
                        ?: source.createDocumentThread(slug, documentId)
            }
    }
