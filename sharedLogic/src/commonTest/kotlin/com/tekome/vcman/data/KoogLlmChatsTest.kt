package com.tekome.vcman.data

import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.http.client.KoogHttpClientException
import ai.koog.http.client.ktor.KtorKoogHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KoogLlmChatsTest {
    private class CapturedRequest(
        val url: String,
        val headers: Map<String, String>,
        val body: String,
    )

    private suspend fun requestSentBy(
        type: LlmProviderType,
        baseUrl: String,
        model: String = "my-custom-model",
        key: String = "k",
    ): Pair<CapturedRequest, Throwable> {
        var captured: CapturedRequest? = null
        val engine =
            MockEngine { request ->
                if (captured == null) {
                    captured =
                        CapturedRequest(
                            url = request.url.toString(),
                            headers = request.headers.entries().associate { (name, values) -> name.lowercase() to values.joinToString() },
                            body = (request.body as TextContent).text,
                        )
                }
                respond("denied", HttpStatusCode.Unauthorized)
            }
        val chat = type.createChat(LlmSettings(type, ApiKey(key), baseUrl, model), KtorKoogHttpClient.Factory(HttpClient(engine)))

        val failure = assertFails { chat.complete("system", "user", emptyList()) }
        return checkNotNull(captured) to failure
    }

    @Test
    fun createChat_buildsKoogChatForEveryProviderType() {
        LlmProviderType.entries.forEach { type ->
            assertIs<KoogLlmChat>(type.createChat(validSettings(type), KtorKoogHttpClient.Factory()), type.id)
        }
    }

    @Test
    fun createChat_sendsRequestToExpectedUrlWithAuthAndCustomModel() =
        runTest {
            val cases =
                listOf(
                    listOf(LlmProviderType.OpenAICompatible, "https://api.openai.com/v1", "https://api.openai.com/v1/chat/completions"),
                    listOf(LlmProviderType.OpenAICompatible, "https://api.openai.com", "https://api.openai.com/v1/chat/completions"),
                    listOf(LlmProviderType.OpenAICompatible, " https://api.openai.com/v1/ ", "https://api.openai.com/v1/chat/completions"),
                    listOf(
                        LlmProviderType.OpenAICompatible,
                        "https://proxy.example.com/openai/v1",
                        "https://proxy.example.com/openai/v1/chat/completions",
                    ),
                    listOf(LlmProviderType.OpenAICompatible, "http://localhost:11434", "http://localhost:11434/v1/chat/completions"),
                    listOf(LlmProviderType.OpenAICompatible, "http://localhost:11434/v1", "http://localhost:11434/v1/chat/completions"),
                    listOf(LlmProviderType.AnthropicCompatible, "https://api.anthropic.com", "https://api.anthropic.com/v1/messages"),
                    listOf(
                        LlmProviderType.AnthropicCompatible,
                        "https://gw.example.com/anthropic",
                        "https://gw.example.com/anthropic/v1/messages",
                    ),
                    listOf(
                        LlmProviderType.AnthropicCompatible,
                        "https://gw.example.com/anthropic/v1",
                        "https://gw.example.com/anthropic/v1/messages",
                    ),
                )
            val authHeaders =
                mapOf(
                    LlmProviderType.OpenAICompatible to ("authorization" to "Bearer k"),
                    LlmProviderType.AnthropicCompatible to ("x-api-key" to "k"),
                )

            cases.forEach { (type, baseUrl, expectedUrl) ->
                type as LlmProviderType
                val (request, failure) = requestSentBy(type, baseUrl as String)

                assertEquals(expectedUrl, request.url, "$type $baseUrl")
                authHeaders[type]?.let { (authName, authValue) ->
                    assertEquals(authValue, request.headers[authName], "$type auth header")
                }
                assertTrue("\"model\":\"my-custom-model\"" in request.body, "$type model in body: ${request.body}")
                assertEquals(AnalysisError.ApiError(401), classify(failure, koogErrorRules), "$type failure")
            }
        }

    @Test
    fun chatPaths_followWhetherBaseUrlAlreadyCarriesAPath() {
        mapOf(
            "https://api.openai.com" to "v1/chat/completions",
            "http://localhost:11434" to "v1/chat/completions",
            "https://api.openai.com/v1" to "chat/completions",
            "https://openrouter.ai/api/v1" to "chat/completions",
            "https://host/api/paas/v4" to "chat/completions",
        ).forEach { (url, expected) -> assertEquals(expected, openAiChatPath(url), url) }
        mapOf(
            "https://api.anthropic.com" to "v1/messages",
            "https://gw.example.com/anthropic" to "v1/messages",
            "https://gw.example.com/anthropic/v1" to "messages",
            "https://gw.example.com/v10" to "v1/messages",
        ).forEach { (url, expected) -> assertEquals(expected, anthropicMessagesPath(url), url) }
    }

    @Test
    fun defaultLlmChatResolver_resolvesEveryProviderType() {
        LlmProviderType.entries.forEach { assertIs<KoogLlmChat>(defaultLlmChatResolver(validSettings(it)), it.id) }
    }

    @Test
    fun webSearchKoogTool_delegatesQueryAndFormatsResults() =
        runTest {
            val fakeDelegate =
                WebSearchTool { query ->
                    listOf(WebSearchResult("Title $query", "https://example.com/$query", "snippet"))
                }
            val tool = WebSearchKoogTool(fakeDelegate)

            val output = tool.execute(WebSearchKoogTool.Args("acme"))

            assertEquals("Title: Title acme\nURL: https://example.com/acme\nSnippet: snippet", output)
        }

    @Test
    fun webSearchKoogTool_titleContainingPipeAndSnippetNewlineAreUnambiguous() =
        runTest {
            val fakeDelegate =
                WebSearchTool {
                    listOf(
                        WebSearchResult("Acme Corp | Home", "https://acme.example.com", "Line one\nLine two"),
                        WebSearchResult("Second Result", "https://second.example.com", "snippet"),
                    )
                }
            val tool = WebSearchKoogTool(fakeDelegate)

            val output = tool.execute(WebSearchKoogTool.Args("acme"))

            assertEquals(
                "Title: Acme Corp | Home\nURL: https://acme.example.com\nSnippet: Line one Line two\n\n" +
                    "Title: Second Result\nURL: https://second.example.com\nSnippet: snippet",
                output,
            )
        }

    @Test
    fun webSearchKoogTool_emptyResultsReturnsExplicitNoResultsMessage() =
        runTest {
            val fakeDelegate = WebSearchTool { emptyList() }
            val tool = WebSearchKoogTool(fakeDelegate)

            val output = tool.execute(WebSearchKoogTool.Args("acme"))

            assertEquals("No web search results found for query: acme", output)
        }

    @Test
    fun webSearchKoogTool_propagatesSearchFailure() =
        runTest {
            val failingDelegate = WebSearchTool { throw IllegalStateException("search backend down") }
            val tool = WebSearchKoogTool(failingDelegate)

            assertFailsWith<IllegalStateException> { tool.execute(WebSearchKoogTool.Args("acme")) }
        }

    @Test
    fun webSearchKoogTool_reportsFailureToOnFailureCallbackBeforeRethrowing() =
        runTest {
            val cause = IllegalStateException("search backend down")
            val failingDelegate = WebSearchTool { throw cause }
            var reported: Throwable? = null
            val tool = WebSearchKoogTool(failingDelegate) { reported = it }

            assertFailsWith<IllegalStateException> { tool.execute(WebSearchKoogTool.Args("acme")) }

            assertEquals(cause, reported)
        }

    @Test
    fun webSearchKoogTool_defaultNameIsWebSearch() {
        val tool = WebSearchKoogTool({ emptyList() })

        assertEquals("web_search", tool.name)
    }

    @Test
    fun webSearchKoogTool_acceptsDistinctNameToAvoidToolRegistryCollisions() {
        val first = WebSearchKoogTool({ emptyList() }, name = "web_search_0")
        val second = WebSearchKoogTool({ emptyList() }, name = "web_search_1")

        assertEquals("web_search_0", first.name)
        assertEquals("web_search_1", second.name)
    }

    @Test
    fun koogErrorRules_classifiesAgentGivingUpAsInvalidResponse() {
        val exception = AIAgentMaxNumberOfIterationsReachedException(20)

        val error = classify(exception, koogErrorRules)

        assertIs<AnalysisError.InvalidResponse>(error)
    }

    @Test
    fun koogErrorRules_classifiesKoogHttpClientExceptionAsApiError() {
        val exception = KoogHttpClientException(clientName = "anthropic", statusCode = 429)

        val error = classify(exception, koogErrorRules)

        assertEquals(AnalysisError.ApiError(429), error)
    }

    @Test
    fun koogErrorRules_classifiesKoogHttpClientExceptionWithoutStatusCodeAsNetwork() {
        val exception = KoogHttpClientException(clientName = "anthropic", statusCode = null)

        val error = classify(exception, koogErrorRules)

        assertEquals(AnalysisError.Network, error)
    }
}
