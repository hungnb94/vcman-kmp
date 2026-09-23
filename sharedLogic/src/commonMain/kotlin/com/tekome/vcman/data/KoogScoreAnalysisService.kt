package com.tekome.vcman.data

import com.tekome.vcman.domain.ProjectScoreReport
import com.tekome.vcman.domain.RubricInput
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * [ScoreAnalysisService] implementation backed by Koog. This class only knows about the [LlmChat]
 * port, [WebSearchToolConfig]/[WebSearchTool], [PromptBuilder], and the domain model — every
 * Koog-specific type lives behind [llmChatResolver] (see `KoogLlmChats.kt`), and every Ktor-specific
 * type lives behind [searchToolResolver] (see `HttpClients.kt`), so adding an [LlmProvider] or a
 * [WebSearchTool] never requires changing this class (OCP).
 *
 * The primary constructor is `internal` so tests can inject fakes without depending on Koog,
 * Ktor, or the network; real callers use the public no-arg constructor.
 */
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
