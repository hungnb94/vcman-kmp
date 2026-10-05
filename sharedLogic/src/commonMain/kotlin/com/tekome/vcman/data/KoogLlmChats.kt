package com.tekome.vcman.data

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.exception.AIAgentException
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.KoogHttpClientException
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.serialization.typeToken
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf

private const val MAX_AGENT_ITERATIONS = 20

internal fun koogChatFactoryFor(provider: LlmProvider): LlmChatFactory =
    when (provider) {
        LlmProvider.Anthropic -> {
            LlmChatFactory { apiKey ->
                KoogLlmChat(
                    client = AnthropicLLMClient(apiKey = apiKey.value, httpClientFactory = KtorKoogHttpClient.Factory()),
                    model = AnthropicModels.Sonnet_5,
                )
            }
        }

        LlmProvider.OpenAI -> {
            LlmChatFactory { apiKey ->
                KoogLlmChat(
                    client = OpenAILLMClient(apiKey = apiKey.value, httpClientFactory = KtorKoogHttpClient.Factory()),
                    model = OpenAIModels.Chat.GPT5_6Sol,
                )
            }
        }
    }

private fun String.normalizedBaseUrl(): String = trim().trimEnd('/')

/**
 * Koog appends the request path to the base URL's own path, so a base URL that already carries a path
 * (`https://openrouter.ai/api/v1`) must not receive another `v1/`.
 */
internal fun openAiChatPath(baseUrl: String): String = if (httpPathOf(baseUrl).isEmpty()) "v1/chat/completions" else "chat/completions"

/** Symmetric to [openAiChatPath]: a base URL ending in `/v1` already carries the version segment. */
internal fun anthropicMessagesPath(baseUrl: String): String = if (httpPathOf(baseUrl).split('/').last() == "v1") "messages" else "v1/messages"

internal fun openAiCompatibleChat(
    settings: LlmSettings,
    httpClientFactory: KoogHttpClient.Factory,
): LlmChat {
    val baseUrl = settings.baseUrl.normalizedBaseUrl()
    val model =
        LLModel(
            provider = LLMProvider.OpenAI,
            id = settings.model.trim(),
            capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools, LLMCapability.OpenAIEndpoint.Completions),
        )
    return KoogLlmChat(
        client =
            OpenAILLMClient(
                apiKey = settings.apiKey.value.trim(),
                settings = OpenAIClientSettings(baseUrl = baseUrl, chatCompletionsPath = openAiChatPath(baseUrl)),
                httpClientFactory = httpClientFactory,
            ),
        model = model,
    )
}

internal fun anthropicCompatibleChat(
    settings: LlmSettings,
    httpClientFactory: KoogHttpClient.Factory,
): LlmChat {
    val baseUrl = settings.baseUrl.normalizedBaseUrl()
    val modelId = settings.model.trim()
    val model =
        LLModel(
            provider = LLMProvider.Anthropic,
            id = modelId,
            capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools),
        )
    return KoogLlmChat(
        client =
            AnthropicLLMClient(
                apiKey = settings.apiKey.value.trim(),
                settings =
                    AnthropicClientSettings(
                        // Koog rejects any model missing from this map with "Unsupported model".
                        modelVersionsMap = mapOf(model to modelId),
                        baseUrl = baseUrl,
                        messagesPath = anthropicMessagesPath(baseUrl),
                    ),
                httpClientFactory = httpClientFactory,
            ),
        model = model,
    )
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
                tools.forEach { searchTool ->
                    tool(WebSearchKoogTool(searchTool) { failure -> lastToolFailure = failure })
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
    private val onFailure: (Throwable?) -> Unit = {},
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
                delegate.search(args.query).also { onFailure(null) }
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
