package com.ezragubbay.quire.feature.chat.conversation

import com.ezragubbay.quire.core.network.ChatEvent
import com.ezragubbay.quire.core.network.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatErrorMappingTest {
    private fun failure(
        code: ErrorCode,
        kind: String?,
    ) = ChatEvent.Error(code, kind, "m").toFailure()

    @Test
    fun budgetKind_winsOverCode() = assertEquals(ChatFailure.Budget, failure(ErrorCode.RateLimited, "budget"))

    @Test
    fun aiUnavailableCode() = assertEquals(ChatFailure.AiUnavailable, failure(ErrorCode.AiUnavailable, null))

    @Test
    fun networkKind() = assertEquals(ChatFailure.Network, failure(ErrorCode.Internal, ChatEvent.KIND_NETWORK))

    @Test
    fun plainRateLimited_isProvider() = assertEquals(ChatFailure.Provider, failure(ErrorCode.RateLimited, null))

    @Test
    fun unknownKind_isProvider() = assertEquals(ChatFailure.Provider, failure(ErrorCode.Internal, "quota"))

    @Test
    fun onlyBudgetIsTerminal() {
        assertEquals(false, ChatFailure.Budget.retryable)
        assertEquals(true, ChatFailure.Provider.retryable)
        assertEquals(true, ChatFailure.Network.retryable)
        assertEquals(true, ChatFailure.AiUnavailable.retryable)
    }
}
