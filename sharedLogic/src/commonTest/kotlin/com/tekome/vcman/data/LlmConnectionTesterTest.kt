package com.tekome.vcman.data

import ai.koog.http.client.KoogHttpClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LlmConnectionTesterTest {
    private fun testerThrowing(
        timeoutMillis: Long = 30_000L,
        block: suspend () -> String,
    ) = LlmConnectionTester({ LlmChat { _, _, _ -> block() } }, timeoutMillis)

    @Test
    fun success() =
        runTest {
            assertEquals(ConnectionTestResult.Success, testerThrowing { "OK" }.test(validSettings()))
        }

    @Test
    fun sendsMinimalRequestWithoutTools() =
        runTest {
            var tools: List<WebSearchTool>? = null
            var settingsSeen: LlmSettings? = null
            val tester =
                LlmConnectionTester(
                    { settings ->
                        settingsSeen = settings
                        LlmChat { _, _, received ->
                            tools = received
                            "OK"
                        }
                    },
                    30_000L,
                )
            val settings = validSettings(LlmProviderType.OpenAICompatible)

            tester.test(settings)

            assertEquals(emptyList(), tools)
            assertEquals(settings, settingsSeen)
        }

    @Test
    fun unauthorizedStatuses() =
        runTest {
            listOf(401, 403).forEach { status ->
                val tester = testerThrowing { throw KoogHttpClientException(clientName = "x", statusCode = status) }
                assertEquals(ConnectionTestResult.Unauthorized, tester.test(validSettings()), "$status")
            }
        }

    @Test
    fun otherApiStatusIsReported() =
        runTest {
            val tester = testerThrowing { throw KoogHttpClientException(clientName = "x", statusCode = 500) }

            assertEquals(ConnectionTestResult.Api(500), tester.test(validSettings()))
        }

    @Test
    fun missingStatusAndIoErrorsAreNetwork() =
        runTest {
            val noStatus = testerThrowing { throw KoogHttpClientException(clientName = "x", statusCode = null) }
            val io = testerThrowing { throw IOException("refused") }

            assertEquals(ConnectionTestResult.Network, noStatus.test(validSettings()))
            assertEquals(ConnectionTestResult.Network, io.test(validSettings()))
        }

    @Test
    fun hangingRequestTimesOutAsNetwork() =
        runTest {
            val tester = testerThrowing(timeoutMillis = 1_000L) { awaitCancellation() }

            assertEquals(ConnectionTestResult.Network, tester.test(validSettings()))
        }

    @Test
    fun unknownExceptionIsUnexpected() =
        runTest {
            val tester = testerThrowing { throw IllegalStateException("boom sk-secret") }

            assertEquals(ConnectionTestResult.Unexpected, tester.test(validSettings()))
        }

    @Test
    fun cancellationIsRethrown() =
        runTest {
            val tester = testerThrowing { throw CancellationException("cancelled") }

            assertFailsWith<CancellationException> { tester.test(validSettings()) }
        }
}
