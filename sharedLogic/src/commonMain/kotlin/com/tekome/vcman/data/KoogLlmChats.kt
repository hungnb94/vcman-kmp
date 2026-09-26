package com.tekome.vcman.data

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.exception.AIAgentException
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.http.client.KoogHttpClientException
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf

private val koogHttpClientFactory = KtorKoogHttpClient.Factory()

private const val MAX_AGENT_ITERATIONS = 20

internal fun koogChatFactoryFor(provider: LlmProvider): LlmChatFactory =
    when (provider) {
        LlmProvider.Anthropic -> {
            LlmChatFactory { apiKey ->
                KoogLlmChat(
                    client = AnthropicLLMClient(apiKey = apiKey.value, httpClientFactory = koogHttpClientFactory),
                    model = AnthropicModels.Sonnet_4_5,
                )
            }
        }

        LlmProvider.OpenAI -> {
            LlmChatFactory { apiKey ->
                KoogLlmChat(
                    client = OpenAILLMClient(apiKey = apiKey.value, httpClientFactory = koogHttpClientFactory),
                    model = OpenAIModels.Chat.GPT4o,
                )
            }
        }
    }

internal val koogErrorRules: List<(Throwable) -> AnalysisError?> =
    errorRules +
        listOf(
            { e -> (e as? KoogHttpClientException)?.let { AnalysisError.ApiError(it.statusCode) } },
            { e -> (e as? AIAgentException)?.let { AnalysisError.InvalidResponse(it.message ?: "Agent failed to produce a response") } },
        )

internal class KoogLlmChat(
    private val client: LLMClient,
    private val model: LLModel,
) : LlmChat {
    override suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        tools: List<WebSearchTool>,
    ): String {
        val toolRegistry =
            ToolRegistry {
                tools.forEach { tool(WebSearchKoogTool(it)) }
            }
        val agent =
            AIAgent(
                promptExecutor = MultiLLMPromptExecutor(client),
                llmModel = model,
                toolRegistry = toolRegistry,
                systemPrompt = systemPrompt,
                maxIterations = MAX_AGENT_ITERATIONS,
            )
        return try {
            agent.run(userPrompt)
        } finally {
            client.close()
        }
    }
}

internal class WebSearchKoogTool(
    private val delegate: WebSearchTool,
) : SimpleTool<WebSearchKoogTool.Args>(
        argsType = typeToken(typeOf<Args>()),
        name = "web_search",
        description =
            "Search the live web for up-to-date information. " +
                "Returns one result per line as 'title | url | snippet'.",
    ) {
    @Serializable
    data class Args(
        @property:LLMDescription("The search query text")
        val query: String,
    )

    override suspend fun execute(args: Args): String {
        val results = delegate.search(args.query)
        if (results.isEmpty()) return "No web search results found for query: ${args.query}"
        return results.joinToString("\n") { "${it.title} | ${it.url} | ${it.snippet}" }
    }
}
