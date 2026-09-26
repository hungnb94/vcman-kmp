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
import kotlinx.coroutines.CancellationException
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
            { e ->
                (e as? KoogHttpClientException)?.let {
                    it.statusCode?.let { status -> AnalysisError.ApiError(status) } ?: AnalysisError.Network
                }
            },
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
        var lastToolFailure: Throwable? = null
        val toolRegistry =
            ToolRegistry {
                tools.forEachIndexed { index, searchTool ->
                    val toolName = if (tools.size > 1) "web_search_$index" else "web_search"
                    tool(WebSearchKoogTool(searchTool, name = toolName) { failure -> lastToolFailure = failure })
                }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw lastToolFailure ?: e
        } finally {
            client.close()
        }
    }
}

internal class WebSearchKoogTool(
    private val delegate: WebSearchTool,
    name: String = "web_search",
    private val onFailure: (Throwable) -> Unit = {},
) : SimpleTool<WebSearchKoogTool.Args>(
        argsType = typeToken(typeOf<Args>()),
        name = name,
        description =
            "Search the live web for up-to-date information. " +
                "Returns each result as labeled Title/URL/Snippet lines, separated by blank lines.",
    ) {
    @Serializable
    data class Args(
        @property:LLMDescription("The search query text")
        val query: String,
    )

    override suspend fun execute(args: Args): String {
        val results =
            try {
                delegate.search(args.query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure(e)
                throw e
            }
        if (results.isEmpty()) return "No web search results found for query: ${args.query}"
        return results.joinToString("\n\n") { result ->
            """
            Title: ${result.title.replace('\n', ' ')}
            URL: ${result.url}
            Snippet: ${result.snippet.replace('\n', ' ')}
            """.trimIndent()
        }
    }
}
