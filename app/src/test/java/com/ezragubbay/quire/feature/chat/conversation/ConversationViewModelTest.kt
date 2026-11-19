package com.ezragubbay.quire.feature.chat.conversation

import androidx.lifecycle.SavedStateHandle
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.ChatEvent
import com.ezragubbay.quire.core.network.ChatMessageDto
import com.ezragubbay.quire.core.network.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {
    private class FakeSource(
        var history: ApiResult<List<ChatMessageDto>> = ApiResult.Success(emptyList()),
    ) : ConversationSource {
        var stream = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 16)
        val asked = mutableListOf<String>()
        val reachable = MutableStateFlow(true)

        override fun observeReachable(
            slug: String,
            threadId: String,
        ): Flow<Boolean> = reachable

        override fun observeMessages(
            slug: String,
            threadId: String,
        ): Flow<ApiResult<List<ChatMessageDto>>> = flowOf(history)

        override fun sendMessage(
            slug: String,
            threadId: String,
            question: String,
        ): Flow<ChatEvent> {
            asked += question
            return stream
        }
    }

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun msg(
        id: String,
        role: String,
        content: String,
    ) = ChatMessageDto(id = id, role = role, content = content, createdAt = "2026-01-01T00:00:00Z")

    private fun vm(source: FakeSource) =
        ConversationViewModel(SavedStateHandle(mapOf("courseSlug" to "c", "threadId" to "t")), source)

    @Test
    fun loadsHistoryIntoBubbles() =
        runTest(dispatcher) {
            val vm = vm(FakeSource(ApiResult.Success(listOf(msg("1", "user", "hi"), msg("2", "assistant", "yo")))))
            advanceUntilIdle()
            val s = vm.state.value
            assertEquals(listOf("hi", "yo"), s.bubbles.map { it.text })
            assertEquals(listOf(Role.User, Role.Assistant), s.bubbles.map { it.role })
            assertFalse(s.historyFailed)
        }

    @Test
    fun historyFailureIsFlagged() =
        runTest(dispatcher) {
            val vm = vm(FakeSource(ApiResult.Failure.Server(500)))
            advanceUntilIdle()
            assertTrue(vm.state.value.historyFailed)
        }

    @Test
    fun send_addsUserBubble_thenAssistantAppearsOnFirstDelta_andAccumulates() =
        runTest(dispatcher) {
            val source = FakeSource()
            val vm = vm(source)
            advanceUntilIdle()

            vm.send("What?")
            advanceUntilIdle()
            assertEquals(
                listOf("What?"),
                vm.state.value.bubbles
                    .map { it.text },
            )
            assertTrue(vm.state.value.streaming)

            source.stream.emit(ChatEvent.Delta("Hel"))
            advanceUntilIdle()
            assertEquals(2, vm.state.value.bubbles.size)
            assertEquals(
                Role.Assistant,
                vm.state.value.bubbles[1]
                    .role,
            )
            assertEquals(
                "Hel",
                vm.state.value.bubbles[1]
                    .text,
            )

            source.stream.emit(ChatEvent.Delta("lo"))
            advanceUntilIdle()
            assertEquals(
                "Hello",
                vm.state.value.bubbles[1]
                    .text,
            )
            assertTrue(vm.state.value.streaming)

            source.stream.emit(ChatEvent.Done(msg("a", "assistant", "Hello!")))
            advanceUntilIdle()
            assertEquals(
                "Hello!",
                vm.state.value.bubbles[1]
                    .text,
            )
            assertFalse(vm.state.value.streaming)
            assertFalse(
                vm.state.value.bubbles[1]
                    .stopped,
            )
        }

    @Test
    fun send_ignoredWhenBlankOrStreaming() =
        runTest(dispatcher) {
            val source = FakeSource()
            val vm = vm(source)
            advanceUntilIdle()
            vm.send("   ")
            advanceUntilIdle()
            assertTrue(source.asked.isEmpty())
            vm.send("one")
            advanceUntilIdle()
            vm.send("two")
            advanceUntilIdle()
            assertEquals(listOf("one"), source.asked)
        }

    @Test
    fun stop_keepsPartial_marksStopped_andEndsStreaming() =
        runTest(dispatcher) {
            val source = FakeSource()
            val vm = vm(source)
            advanceUntilIdle()
            vm.send("Q")
            advanceUntilIdle()
            source.stream.emit(ChatEvent.Delta("part"))
            advanceUntilIdle()

            vm.stop()
            advanceUntilIdle()
            assertFalse(vm.state.value.streaming)
            assertEquals(
                "part",
                vm.state.value.bubbles[1]
                    .text,
            )
            assertTrue(
                vm.state.value.bubbles[1]
                    .stopped,
            )

            // Late events after Stop are not collected.
            source.stream.emit(ChatEvent.Delta("more"))
            advanceUntilIdle()
            assertEquals(
                "part",
                vm.state.value.bubbles[1]
                    .text,
            )
        }

    @Test
    fun error_keepsPartial_andShowsError() =
        runTest(dispatcher) {
            val source = FakeSource()
            val vm = vm(source)
            advanceUntilIdle()
            vm.send("Q")
            advanceUntilIdle()
            source.stream.emit(ChatEvent.Delta("par"))
            source.stream.emit(ChatEvent.Error(ErrorCode.Internal, null, "boom"))
            advanceUntilIdle()
            assertFalse(vm.state.value.streaming)
            assertEquals(
                "par",
                vm.state.value.bubbles[1]
                    .text,
            )
            assertEquals(ChatFailure.Provider, vm.state.value.error)
            assertTrue(vm.state.value.retryable)
        }

    @Test
    fun newSend_clearsPreviousError() =
        runTest(dispatcher) {
            val source = FakeSource()
            val vm = vm(source)
            advanceUntilIdle()
            vm.send("Q")
            advanceUntilIdle()
            source.stream.emit(ChatEvent.Error(ErrorCode.AiUnavailable, null, "down"))
            advanceUntilIdle()
            vm.send("Q2")
            advanceUntilIdle()
            assertNull(vm.state.value.error)
        }

    @Test
    fun budgetError_isTerminal_noRetry_partialKept() =
        runTest(dispatcher) {
            val source = FakeSource()
            val vm = vm(source)
            advanceUntilIdle()
            vm.send("Q")
            advanceUntilIdle()
            source.stream.emit(ChatEvent.Delta("par"))
            source.stream.emit(ChatEvent.Error(ErrorCode.RateLimited, ChatEvent.KIND_BUDGET, "budget"))
            advanceUntilIdle()
            assertEquals(ChatFailure.Budget, vm.state.value.error)
            assertFalse(vm.state.value.retryable)
            assertEquals(
                "par",
                vm.state.value.bubbles[1]
                    .text,
            )
            vm.retry()
            advanceUntilIdle()
            assertEquals(listOf("Q"), source.asked)
        }

    @Test
    fun rateLimitedWithoutBudgetKind_isRetryable() =
        runTest(dispatcher) {
            val source = FakeSource()
            val vm = vm(source)
            advanceUntilIdle()
            vm.send("Q")
            advanceUntilIdle()
            source.stream.emit(ChatEvent.Error(ErrorCode.RateLimited, null, "slow down"))
            advanceUntilIdle()
            assertEquals(ChatFailure.Provider, vm.state.value.error)
            assertTrue(vm.state.value.retryable)
        }

    @Test
    fun preStreamAiUnavailable_isRetryableWithOwnFailure() =
        runTest(dispatcher) {
            val source = FakeSource()
            val vm = vm(source)
            advanceUntilIdle()
            vm.send("Q")
            advanceUntilIdle()
            source.stream.emit(ChatEvent.Error(ErrorCode.AiUnavailable, null, ""))
            advanceUntilIdle()
            assertEquals(ChatFailure.AiUnavailable, vm.state.value.error)
            assertTrue(vm.state.value.retryable)
            assertEquals(1, vm.state.value.bubbles.size)
        }

    @Test
    fun networkDropMidStream_keepsPartial_isRetryable() =
        runTest(dispatcher) {
            val source = FakeSource()
            val vm = vm(source)
            advanceUntilIdle()
            vm.send("Q")
            advanceUntilIdle()
            source.stream.emit(ChatEvent.Delta("par"))
            source.stream.emit(ChatEvent.Error(ErrorCode.Internal, ChatEvent.KIND_NETWORK, "reset"))
            advanceUntilIdle()
            assertEquals(ChatFailure.Network, vm.state.value.error)
            assertTrue(vm.state.value.retryable)
            assertEquals(
                "par",
                vm.state.value.bubbles[1]
                    .text,
            )
        }

    @Test
    fun retry_resendsSameQuestion_noDuplicateUserBubble_replacesFailedBubble() =
        runTest(dispatcher) {
            val source = FakeSource()
            val vm = vm(source)
            advanceUntilIdle()
            vm.send("Q")
            advanceUntilIdle()
            source.stream.emit(ChatEvent.Delta("par"))
            source.stream.emit(ChatEvent.Error(ErrorCode.Internal, null, "boom"))
            advanceUntilIdle()

            source.stream = MutableSharedFlow(extraBufferCapacity = 16)
            vm.retry()
            advanceUntilIdle()
            assertEquals(listOf("Q", "Q"), source.asked)
            assertNull(vm.state.value.error)
            assertTrue(vm.state.value.streaming)
            assertEquals(
                listOf("Q"),
                vm.state.value.bubbles
                    .map { it.text },
            )

            source.stream.emit(ChatEvent.Delta("ok"))
            source.stream.emit(ChatEvent.Done(msg("a", "assistant", "ok!")))
            advanceUntilIdle()
            assertEquals(
                listOf("Q", "ok!"),
                vm.state.value.bubbles
                    .map { it.text },
            )
            assertEquals(
                listOf(Role.User, Role.Assistant),
                vm.state.value.bubbles
                    .map { it.role },
            )
            assertFalse(vm.state.value.streaming)
        }

    @Test
    fun retry_withoutFailure_isNoOp() =
        runTest(dispatcher) {
            val source = FakeSource()
            val vm = vm(source)
            advanceUntilIdle()
            vm.retry()
            advanceUntilIdle()
            assertTrue(source.asked.isEmpty())
        }

    @Test
    fun offline_flagFollowsReachability_andBlocksSend() =
        runTest(dispatcher) {
            val source = FakeSource()
            val vm = vm(source)
            advanceUntilIdle()
            assertFalse(vm.state.value.offline)

            source.reachable.value = false
            advanceUntilIdle()
            assertTrue(vm.state.value.offline)
            vm.send("Q")
            advanceUntilIdle()
            assertTrue(source.asked.isEmpty())

            source.reachable.value = true
            advanceUntilIdle()
            assertFalse(vm.state.value.offline)
            vm.send("Q")
            advanceUntilIdle()
            assertEquals(listOf("Q"), source.asked)
        }
}
