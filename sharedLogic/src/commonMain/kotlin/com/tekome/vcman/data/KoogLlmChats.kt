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

/**
 * The single file allowed to import `ai.koog.*` (see Architecture in the implementation plan).
 * Every other file in this module talks to [LlmChat]/[WebSearchTool] only, so a Koog API change
 * is contained here.
 */

/** A Koog `HttpClient.Factory`, reused across calls so LLM requests share one engine/connection pool. */
private val koogHttpClientFactory = KtorKoogHttpClient.Factory()

private const val MAX_AGENT_ITERATIONS = 20

/**
 * The single, exhaustive extension point for the LLM-provider axis. `when` has no `else` branch:
 * adding [LlmProvider.Anthropic]/[LlmProvider.OpenAI]'s sibling later is a compile error until a
 * branch is added here — the "provider missing its wiring" state cannot exist at runtime.
 */
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

/**
 * Extends the base [errorRules] with the Koog-specific failure types discovered while wiring this
 * adapter: an HTTP failure surfaced by [koogHttpClientFactory] ([KoogHttpClientException]), and the
 * agent giving up without a usable response ([AIAgentException], e.g. hitting [MAX_AGENT_ITERATIONS]
 * without finishing). Without this second rule, [AIAgentException] would otherwise fail `classify`'s
 * every rule and leak out of [KoogScoreAnalysisService.analyze] as a raw, un-[AnalysisException]
 * Koog type — breaking the "every Koog-specific type stays behind this port" contract.
 */
internal val koogErrorRules: List<(Throwable) -> AnalysisError?> =
    errorRules +
        listOf<(Throwable) -> AnalysisError?>(
            { e -> (e as? KoogHttpClientException)?.let { AnalysisError.ApiError(it.statusCode) } },
            { e -> (e as? AIAgentException)?.let { AnalysisError.InvalidResponse(it.message ?: "Agent failed to produce a response") } },
        )

/**
 * [LlmChat] adapter backed by a Koog [AIAgent]. Every [WebSearchTool] passed in is wrapped by the
 * same [WebSearchKoogTool] adapter, so a new search backend never needs its own Koog wiring.
 */
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
        // `client` is constructed fresh per call (see `koogChatFactoryFor`) and owns its own
        // `KoogHttpClient`/connection pool; `LLMClient` is `AutoCloseable` and its contract says to
        // "always close it when finished", so it must be closed here or every `analyze()` call leaks
        // an HTTP client.
        return try {
            agent.run(userPrompt)
        } finally {
            client.close()
        }
    }
}

/**
 * The single Koog tool adapter for every [WebSearchTool] implementation. A new search backend
 * (Firecrawl/future) never needs its own Koog wiring: it only implements [WebSearchTool],
 * and this adapter exposes it to the agent uniformly.
 */
internal class WebSearchKoogTool(
    private val delegate: WebSearchTool,
) : SimpleTool<WebSearchKoogTool.Args>(
        // `typeToken<Args>()` (Koog's own reified inline factory) is avoided on purpose: Koog's
        // android artifact is compiled targeting JVM 17, and inlining that bytecode conflicts
        // with this module's JVM 11 target. `typeOf<Args>()` is a kotlin-stdlib inline function
        // (safe to inline into JVM 11 bytecode); `typeToken(KType)` is a regular (non-inline)
        // Koog function, so no Koog bytecode gets inlined across the JVM-target boundary either.
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
