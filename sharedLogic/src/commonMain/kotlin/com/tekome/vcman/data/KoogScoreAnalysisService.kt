package com.tekome.vcman.data

import com.tekome.vcman.domain.ProjectScoreReport
import com.tekome.vcman.domain.RubricInput
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class KoogScoreAnalysisService internal constructor(
    private val llmChatResolver: LlmChatResolver,
    private val searchToolResolver: (WebSearchToolConfig) -> WebSearchTool,
    private val promptBuilder: PromptBuilder,
    private val nowMillis: () -> Long,
) : ScoreAnalysisService {
    @OptIn(ExperimentalTime::class)
    constructor() : this(
        llmChatResolver = ::koogChatFactoryFor,
        searchToolResolver = defaultSearchToolResolver(),
        promptBuilder = PromptBuilder,
        nowMillis = { Clock.System.now().toEpochMilliseconds() },
    )

    override suspend fun analyze(
        rubric: RubricInput,
        subjectQuery: String,
        config: LlmRequestConfig,
    ): Result<ProjectScoreReport> =
        runAnalysis(rules = koogErrorRules) {
            val tools = listOfNotNull(config.searchTool?.let(searchToolResolver))
            val chat = llmChatResolver(config.provider).create(config.apiKey)
            val raw =
                chat.complete(
                    systemPrompt = promptBuilder.buildSystemPrompt(rubric),
                    userPrompt = promptBuilder.buildUserPrompt(subjectQuery),
                    tools = tools,
                )
            decodeScoreReport(raw, rubric.title, nowMillis())
        }
}
