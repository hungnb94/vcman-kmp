package com.tekome.vcman.data

import com.tekome.vcman.domain.ProjectScoreReport
import com.tekome.vcman.domain.RubricInput

/**
 * Provider-agnostic entry point for running an LLM-backed score analysis.
 *
 * Implementations must not hard-code behavior for a single [LlmProvider]: switching
 * [LlmRequestConfig.provider] or [LlmRequestConfig.searchTool] must never require changes at the
 * call site, only a different [LlmRequestConfig] value.
 */
interface ScoreAnalysisService {
    suspend fun analyze(
        rubric: RubricInput,
        subjectQuery: String,
        config: LlmRequestConfig,
    ): Result<ProjectScoreReport>
}

/**
 * The set of LLM providers this POC can route to.
 *
 * Modeled as a `sealed interface` (not `enum class`) so a future provider that needs extra state
 * (for example a self-hosted `Ollama(baseUrl: String)`) can be added as a new case without
 * changing the existing ones or the type of the ones already shipped.
 */
sealed interface LlmProvider {
    val displayName: String

    data object Anthropic : LlmProvider {
        override val displayName: String = "Anthropic"
    }

    data object OpenAI : LlmProvider {
        override val displayName: String = "OpenAI"
    }
}

/**
 * One analysis request: which provider to call, its key, and an optional web-search backend.
 *
 * Deliberately not a `data class`: the compiler-generated `toString()`/`copy()` of a `data class`
 * would defeat [ApiKey]'s redaction the moment this config is interpolated into a log line.
 */
class LlmRequestConfig(
    val provider: LlmProvider,
    val apiKey: ApiKey,
    val searchTool: WebSearchToolConfig?,
) {
    override fun toString(): String = "LlmRequestConfig(provider=$provider, searchTool=$searchTool)"
}
