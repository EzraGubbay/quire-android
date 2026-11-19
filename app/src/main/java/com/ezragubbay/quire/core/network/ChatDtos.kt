package com.ezragubbay.quire.core.network

import kotlinx.serialization.Serializable

/** `Thread` of the mobile API. [documentId] is set for a document-scoped chat. */
@Serializable
data class ThreadDto(
    val id: String,
    val courseSlug: String,
    val title: String,
    val documentId: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

/** A cited source. Carries no web-only `href`; the app resolves [id]/[pageNo] through its own viewer. */
@Serializable
data class CitationDto(
    val n: Int,
    val kind: String,
    val id: String,
    val title: String,
    val pageNo: Int? = null,
)

/** `ChatMessage`: [role] is `user` or `assistant`; [error] is a persisted failure note, if any. */
@Serializable
data class ChatMessageDto(
    val id: String,
    val role: String,
    val content: String,
    val citations: List<CitationDto> = emptyList(),
    val error: String? = null,
    val createdAt: String,
)

@Serializable
data class CreateThreadRequest(
    val title: String? = null,
    val documentId: String? = null,
)

@Serializable
data class PostMessageRequest(
    val question: String,
)

/** Canonical `mobile-v1` `ErrorCode` enum. [Unknown] only covers a future server value outside the enum. */
enum class ErrorCode(
    val wire: String,
) {
    Unauthorized("unauthorized"),
    NotFound("not_found"),
    InvalidRequest("invalid_request"),
    RateLimited("rate_limited"),
    AiUnavailable("ai_unavailable"),
    Internal("internal"),
    Unknown("unknown"),
    ;

    companion object {
        fun fromWire(value: String?): ErrorCode = entries.firstOrNull { it.wire == value } ?: Unknown
    }
}
