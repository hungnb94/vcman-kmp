package com.tekome.vcman.presentation

import com.tekome.vcman.data.ApiKey
import com.tekome.vcman.data.ConnectionTestResult
import com.tekome.vcman.data.WebSearchKeyTester
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class KeyCheckTest {
    private class GatedTester : WebSearchKeyTester {
        val calls = mutableListOf<ApiKey>()
        val gate = CompletableDeferred<ConnectionTestResult>()

        override suspend fun test(key: ApiKey): ConnectionTestResult {
            calls += key
            return gate.await()
        }
    }

    @Test
    fun reportsTestingThenTheResult() =
        runTest(UnconfinedTestDispatcher()) {
            val tester = GatedTester()
            val states = mutableListOf<ConnectionTestState>()
            val check = KeyCheck(backgroundScope, tester) { states += it }

            check.run(ApiKey("fc"))
            tester.gate.complete(ConnectionTestResult.Success)

            assertEquals(listOf(ConnectionTestState.Testing, ConnectionTestState.Done(ConnectionTestResult.Success)), states)
        }

    @Test
    fun blankKeyAndSecondRunWhileTestingAreIgnored() =
        runTest(UnconfinedTestDispatcher()) {
            val tester = GatedTester()
            val check = KeyCheck(backgroundScope, tester) {}

            check.run(ApiKey(" "))
            check.run(ApiKey("fc-1"))
            check.run(ApiKey("fc-2"))

            assertEquals(listOf(ApiKey("fc-1")), tester.calls)
        }

    @Test
    fun resetCancelsSoALateResultIsDropped() =
        runTest(UnconfinedTestDispatcher()) {
            val tester = GatedTester()
            val states = mutableListOf<ConnectionTestState>()
            val check = KeyCheck(backgroundScope, tester) { states += it }

            check.run(ApiKey("fc"))
            check.reset()
            tester.gate.complete(ConnectionTestResult.Success)

            assertEquals(listOf(ConnectionTestState.Testing, ConnectionTestState.Idle), states)
        }

    @Test
    fun testerCrashBecomesUnexpected() =
        runTest(UnconfinedTestDispatcher()) {
            val states = mutableListOf<ConnectionTestState>()
            val check = KeyCheck(backgroundScope, { error("boom") }) { states += it }

            check.run(ApiKey("fc"))

            assertEquals(ConnectionTestState.Done(ConnectionTestResult.Unexpected), states.last())
        }
}
