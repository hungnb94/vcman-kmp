package com.tekome.vcman.data

import com.tekome.vcman.domain.ProjectScoreReport
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Closed set of failure categories [ScoreAnalysisService.analyze] can return, so callers (a
 * ViewModel) can react differently to a network hiccup vs an invalid API key vs a malformed LLM
 * response vs an ambiguous subject without inspecting exception messages.
 */
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

/** Wraps an [AnalysisError] as a `Throwable` so it can travel through `Result.failure`. */
class AnalysisException(
    val error: AnalysisError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

/**
 * Ordered rules mapping a *type* of exception to an [AnalysisError]. Each rule inspects one
 * exception at a time; [classify] walks the `cause` chain so an exception wrapped by an
 * intermediate layer (Koog, Ktor) is still recognized. Extending to a new exception type means
 * appending a rule (see [koogErrorRules]); existing rules are never edited (OCP).
 *
 * Only generic Ktor/kotlinx.serialization exception types are classified here. Koog-specific
 * exception types are added by [koogErrorRules] in `KoogLlmChats.kt`, the only file allowed to
 * import `ai.koog.*`.
 */
internal val errorRules: List<(Throwable) -> AnalysisError?> =
    listOf(
        { e -> (e as? ResponseException)?.let { AnalysisError.ApiError(it.response.status.value) } },
        { e -> if (e is HttpRequestTimeoutException || e is IOException) AnalysisError.Network else null },
        { e -> if (e is SerializationException) AnalysisError.InvalidResponse("LLM response is not valid JSON") else null },
    )

/** Safety bound on how many links of the `cause` chain to walk, in case of a cyclic chain. */
private const val MAX_CAUSE_CHAIN_DEPTH = 8

/**
 * Walks [throwable] and its `cause` chain (bounded, in case of a cycle), returning the first
 * [AnalysisError] any [rules] entry recognizes, or `null` if none does.
 */
internal fun classify(
    throwable: Throwable,
    rules: List<(Throwable) -> AnalysisError?> = errorRules,
): AnalysisError? =
    generateSequence(throwable) { it.cause }
        .take(MAX_CAUSE_CHAIN_DEPTH)
        .firstNotNullOfOrNull { candidate -> rules.firstNotNullOfOrNull { rule -> rule(candidate) } }

/**
 * Runs [block], turning any exception into a `Result.failure` carrying a classified
 * [AnalysisException]. `CancellationException` is always rethrown, never swallowed into a
 * `Result` (structured concurrency must keep working). An [AnalysisException] thrown deliberately
 * by [block] (for example [decodeScoreReport]'s ambiguity/invalid-response checks) passes through
 * unchanged. Any other exception [classify] cannot recognize is returned unwrapped rather than
 * mislabeled as one of the four known categories.
 */
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
