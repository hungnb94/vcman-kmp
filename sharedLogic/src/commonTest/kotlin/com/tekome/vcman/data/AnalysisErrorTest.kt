package com.tekome.vcman.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalysisErrorTest {
    @Test
    fun classify_responseExceptionBecomesApiErrorWithStatus() =
        runTest {
            val engine = MockEngine { respond("nope", HttpStatusCode.TooManyRequests) }
            val http = HttpClient(engine) { expectSuccess = true }

            val error =
                try {
                    http.get("https://example.invalid")
                    null
                } catch (e: Exception) {
                    classify(e)
                } finally {
                    http.close()
                }

            assertEquals(AnalysisError.ApiError(429), error)
        }

    @Test
    fun classify_ioExceptionBecomesNetwork() {
        assertEquals(AnalysisError.Network, classify(IOException("socket closed")))
    }

    @Test
    fun classify_requestTimeoutBecomesNetwork() {
        val timeout = HttpRequestTimeoutException("https://example.invalid", 1000)

        assertEquals(AnalysisError.Network, classify(timeout))
    }

    @Test
    fun classify_findsNetworkErrorWrappedInsideForeignException() {
        val wrapped = IllegalStateException("agent framework failed", IOException("socket closed"))

        assertEquals(AnalysisError.Network, classify(wrapped))
    }

    @Test
    fun classify_serializationExceptionBecomesInvalidResponse() {
        assertEquals(
            AnalysisError.InvalidResponse("LLM response is not valid JSON"),
            classify(SerializationException("bad json")),
        )
    }

    @Test
    fun classify_unknownExceptionReturnsNull() {
        assertNull(classify(IllegalStateException("some unrelated bug")))
    }

    @Test
    fun classify_cyclicCauseChainTerminates() {
        lateinit var first: CyclicThrowable
        val second = CyclicThrowable("second") { first }
        first = CyclicThrowable("first") { second }

        assertNull(classify(first))
    }

    @Test
    fun runAnalysis_rethrowsCancellationException() =
        runTest {
            assertFailsWith<CancellationException> {
                runAnalysis { throw CancellationException("stop") }
            }
        }

    @Test
    fun runAnalysis_returnsUnclassifiedExceptionUnwrapped() =
        runTest {
            val original = IllegalStateException("unexpected bug")

            val result = runAnalysis { throw original }

            assertEquals(original, result.exceptionOrNull())
        }

    @Test
    fun runAnalysis_wrapsClassifiableExceptionAsAnalysisException() =
        runTest {
            val result = runAnalysis { throw IOException("connection reset") }

            val exception = assertIs<AnalysisException>(result.exceptionOrNull())
            assertEquals(AnalysisError.Network, exception.error)
        }

    @Test
    fun analysisException_messageContainsOnlyClassifiedErrorNotRawExceptionMessage() {
        val secretMessage = "leaked-api-key-in-body=sk-123"
        val cause = RuntimeException(secretMessage)
        val exception = AnalysisException(AnalysisError.ApiError(500), cause)

        assertEquals(AnalysisError.ApiError(500).toString(), exception.message)
        assertTrue(secretMessage !in (exception.message ?: ""))
    }

    @Test
    fun classify_wrappedAnalysisExceptionUnwrapsToOriginalError() {
        val originalError = AnalysisError.AmbiguousSubject("Ambiguous: X Inc vs X Corp")
        val analysisException = AnalysisException(originalError)
        val wrapped = IllegalStateException("agent framework wrapper", analysisException)

        assertEquals(originalError, classify(wrapped))
    }

    @Test
    fun runAnalysis_passesThroughAnalysisExceptionUnchanged() =
        runTest {
            val originalError = AnalysisError.InvalidResponse("bad json")
            val originalException = AnalysisException(originalError)

            val result = runAnalysis { throw originalException }

            val exception = assertIs<AnalysisException>(result.exceptionOrNull())
            assertEquals(originalError, exception.error)
            assertEquals(originalException, exception)
        }
}

private class CyclicThrowable(
    message: String,
    private val causeProvider: () -> Throwable,
) : Exception(message) {
    override val cause: Throwable
        get() = causeProvider()
}