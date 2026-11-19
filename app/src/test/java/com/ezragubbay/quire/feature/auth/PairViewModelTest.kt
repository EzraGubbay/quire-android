package com.ezragubbay.quire.feature.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class PairViewModelTest {
    private class FakePairer(
        var result: PairResult = PairResult.Success,
    ) : Pairer {
        val calls = mutableListOf<Triple<String, String, String>>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun pair(
            serverUrl: String,
            code: String,
            deviceName: String,
        ): PairResult {
            calls += Triple(serverUrl, code, deviceName)
            gate?.await()
            return result
        }
    }

    private val pairer = FakePairer()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() =
        PairViewModel(pairer).apply {
            onServerUrlChange("https://quire.example.com")
            onCodeChange("abc12345")
            onDeviceNameChange("Pixel 9")
        }

    @Test
    fun initialState_prefillsHttps_andCannotPair() {
        val state = PairViewModel(pairer).state.value
        assertEquals("https://", state.serverUrl)
        assertEquals("", state.code)
        assertFalse(state.canPair)
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @Test
    fun codeInput_isUppercased_filtered_andCappedAtEight() {
        val vm = PairViewModel(pairer)
        vm.onCodeChange("ab c-12 3456789")
        assertEquals("ABC12345", vm.state.value.code)
    }

    @Test
    fun canPair_requiresFullCode_andDeviceName() {
        val vm = vm()
        assertTrue(vm.state.value.canPair)
        vm.onCodeChange("ABC")
        assertFalse(vm.state.value.canPair)
        vm.onCodeChange("ABC12345")
        vm.onDeviceNameChange("  ")
        assertFalse(vm.state.value.canPair)
    }

    @Test
    fun pair_showsLoading_thenEmitsPaired_onSuccess() =
        runTest {
            val vm = vm()
            pairer.gate = CompletableDeferred()
            vm.onPair()
            assertTrue(vm.state.value.loading)
            pairer.gate!!.complete(Unit)
            vm.paired.first()
            assertFalse(vm.state.value.loading)
            assertNull(vm.state.value.error)
            assertEquals(listOf(Triple("https://quire.example.com", "ABC12345", "Pixel 9")), pairer.calls)
        }

    @Test
    fun pairFailure_surfacesError_andStaysIdle() {
        PairError.entries.filter { it != PairError.InvalidUrl }.forEach { error ->
            pairer.result = PairResult.Failure(error)
            val vm = vm()
            vm.onPair()
            assertEquals(error, vm.state.value.error)
            assertFalse(vm.state.value.loading)
        }
    }

    @Test
    fun editingAnyField_clearsError() {
        pairer.result = PairResult.Failure(PairError.InvalidCode)
        val vm = vm()
        vm.onPair()
        vm.onCodeChange("ZZZZ9999")
        assertNull(vm.state.value.error)
    }

    @Test
    fun invalidUrl_isRejectedWithoutCallingServer() {
        val vm = vm()
        vm.onServerUrlChange("http://quire.example.com")
        vm.onPair()
        assertEquals(PairError.InvalidUrl, vm.state.value.error)
        assertTrue(pairer.calls.isEmpty())
    }

    @Test
    fun onPair_whileLoading_isIgnored() {
        val vm = vm()
        pairer.gate = CompletableDeferred()
        vm.onPair()
        vm.onPair()
        assertEquals(1, pairer.calls.size)
    }
}
