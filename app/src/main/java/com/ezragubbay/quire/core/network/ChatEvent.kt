package com.ezragubbay.quire.core.network

/** One event of an in-flight answer. A stream is `User`, zero or more `Delta`, then `Done` or `Error`. */
sealed interface ChatEvent {
    /** The server persisted the user's question. */
    data class User(
        val message: ChatMessageDto,
    ) : ChatEvent

    /** Next chunk of the assistant answer; append to the bubble. */
    data class Delta(
        val text: String,
    ) : ChatEvent

    /** The settled assistant message (with citations). Terminal. */
    data class Done(
        val message: ChatMessageDto,
    ) : ChatEvent

    /**
     * Terminal failure. [code] is the canonical [ErrorCode]; [kind] is an open hint. Only `kind == "budget"` is
     * non-retryable; any other (or absent) kind may be retried by re-asking the same question.
     */
    data class Error(
        val code: ErrorCode,
        val kind: String?,
        val message: String,
    ) : ChatEvent {
        val retryable: Boolean get() = kind != KIND_BUDGET
    }

    companion object {
        const val KIND_BUDGET = "budget"
        const val KIND_NETWORK = "network"
    }
}
