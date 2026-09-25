package com.tekome.vcman.data

import com.tekome.vcman.domain.ProjectScoreReport
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

sealed interface AnalysisError {
    data object Network : AnalysisError

    data class ApiError(
        val httpStatus: Int?,
    ) : AnalysisError

    data class InvalidResponse(
        val reason: String,
    ) : AnalysisError

    data class AmbiguousSubject(
        val explanation: String,
    ) : AnalysisError
}

class AnalysisException(
    val error: AnalysisError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

internal val errorRules: List<(Throwable) -> AnalysisError?> =
    listOf(
        { e -> (e as? AnalysisException)?.error },
        { e -> (e as? ResponseException)?.let { AnalysisError.ApiError(it.response.status.value) } },
        { e -> if (e is HttpRequestTimeoutException || e is IOException) AnalysisError.Network else null },
        { e -> if (e is SerializationException) AnalysisError.InvalidResponse("LLM response is not valid JSON") else null },
    )

private const val MAX_CAUSE_CHAIN_DEPTH = 8

internal fun classify(
    throwable: Throwable,
    rules: List<(Throwable) -> AnalysisError?> = errorRules,
): AnalysisError? =
    generateSequence(throwable) { it.cause }
        .take(MAX_CAUSE_CHAIN_DEPTH)
        .firstNotNullOfOrNull { candidate -> rules.firstNotNullOfOrNull { rule -> rule(candidate) } }

internal suspend fun runAnalysis(
    rules: List<(Throwable) -> AnalysisError?> = errorRules,
    block: suspend () -> ProjectScoreReport,
): Result<ProjectScoreReport> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: AnalysisException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(classify(e, rules)?.let { AnalysisException(it, e) } ?: e)
    }
