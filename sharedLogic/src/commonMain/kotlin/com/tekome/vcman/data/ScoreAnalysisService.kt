package com.tekome.vcman.data

import com.tekome.vcman.domain.LanguageTag
import com.tekome.vcman.domain.ProjectScoreReport
import com.tekome.vcman.domain.RubricInput

interface ScoreAnalysisService {
    suspend fun analyze(
        rubric: RubricInput,
        subjectQuery: String,
        config: LlmRequestConfig,
        outputLanguage: LanguageTag,
    ): Result<ProjectScoreReport>
}

class LlmRequestConfig(
    val settings: LlmSettings,
    val searchTool: WebSearchToolConfig?,
) {
    override fun toString(): String = "LlmRequestConfig(providerType=${settings.providerType}, searchTool=$searchTool)"
}
