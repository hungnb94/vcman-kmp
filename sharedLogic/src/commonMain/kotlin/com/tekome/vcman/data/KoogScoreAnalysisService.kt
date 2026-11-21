package com.tekome.vcman.data

import com.tekome.vcman.domain.LanguageTag
import com.tekome.vcman.domain.ProjectScoreReport
import com.tekome.vcman.domain.RubricInput
import kotlinx.coroutines.CancellationException
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
        llmChatResolver = defaultLlmChatResolver,
        searchToolResolver = defaultSearchToolResolver(),
        promptBuilder = PromptBuilder,
        nowMillis = { Clock.System.now().toEpochMilliseconds() },
    )

    override suspend fun analyze(
        rubric: RubricInput,
        subjectQuery: String,
        config: LlmRequestConfig,
        outputLanguage: LanguageTag,
    ): Result<ProjectScoreReport> =
        runAnalysis(rules = koogErrorRules) {
            val search = config.searchTool?.let(searchToolResolver)?.let(::RecordingWebSearchTool)
            val chat = llmChatResolver(config.settings)
            val raw =
                try {
                    chat.complete(
                        systemPrompt = promptBuilder.buildSystemPrompt(rubric),
                        userPrompt = promptBuilder.buildUserPrompt(subjectQuery, outputLanguage),
                        tools = listOfNotNull(search),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw search?.rejection ?: e
                }
            // The agent may swallow a tool error and still answer; a refused key must not pass silently.
            search?.rejection?.let { throw it }
            decodeScoreReport(raw, rubric.title, nowMillis()).copy(webSearchSkipped = search?.skipped == true)
        }
}
