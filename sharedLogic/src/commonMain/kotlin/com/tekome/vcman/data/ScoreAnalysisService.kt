package com.tekome.vcman.data

import com.tekome.vcman.domain.ProjectScoreReport
import com.tekome.vcman.domain.RubricInput

interface ScoreAnalysisService {
    suspend fun analyze(
        rubric: RubricInput,
        subjectQuery: String,
        config: LlmRequestConfig,
    ): Result<ProjectScoreReport>
}

sealed interface LlmProvider {
    val displayName: String

    data object Anthropic : LlmProvider {
        override val displayName: String = "Anthropic"
    }

    data object OpenAI : LlmProvider {
        override val displayName: String = "OpenAI"
    }
}

class LlmRequestConfig(
    val provider: LlmProvider,
    val apiKey: ApiKey,
    val searchTool: WebSearchToolConfig?,
) {
    override fun toString(): String = "LlmRequestConfig(provider=$provider, searchTool=$searchTool)"
}
