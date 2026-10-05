package com.tekome.vcman.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/** Outcome of a "Test connection" request. UI maps each case to a localized message. */
sealed interface ConnectionTestResult {
    data object Success : ConnectionTestResult

    data object Unauthorized : ConnectionTestResult

    data object Network : ConnectionTestResult

    data class Api(
        val httpStatus: Int?,
    ) : ConnectionTestResult

    data object Unexpected : ConnectionTestResult
}

fun interface ConnectionTester {
    suspend fun test(settings: LlmSettings): ConnectionTestResult
}

private const val TEST_TIMEOUT_MILLIS = 30_000L
private val unauthorizedStatuses = setOf(401, 403)

/** Sends one minimal chat turn through the same builder used for analysis, so every provider gets this for free. */
class LlmConnectionTester internal constructor(
    private val resolver: LlmChatResolver,
    private val timeoutMillis: Long,
) : ConnectionTester {
    constructor() : this(defaultLlmChatResolver, TEST_TIMEOUT_MILLIS)

    override suspend fun test(settings: LlmSettings): ConnectionTestResult =
        try {
            withTimeout(timeoutMillis) {
                resolver(settings).complete(systemPrompt = "Reply with OK.", userPrompt = "ping", tools = emptyList())
            }
            ConnectionTestResult.Success
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            ConnectionTestResult.Network
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classify(e, koogErrorRules).toTestResult()
        }
}

private fun AnalysisError?.toTestResult(): ConnectionTestResult =
    when (this) {
        AnalysisError.Network -> ConnectionTestResult.Network
        is AnalysisError.ApiError ->
            if (httpStatus in unauthorizedStatuses) ConnectionTestResult.Unauthorized else ConnectionTestResult.Api(httpStatus)
        is AnalysisError.InvalidResponse, is AnalysisError.AmbiguousSubject, null -> ConnectionTestResult.Unexpected
    }
