package com.tekome.vcman.data

import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.http.client.KoogHttpClientException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class KoogLlmChatsTest {
    @Test
    fun koogChatFactoryFor_returnsFactoryForEveryProvider() {
        val anthropicChat = koogChatFactoryFor(LlmProvider.Anthropic).create(ApiKey("key"))
        val openAiChat = koogChatFactoryFor(LlmProvider.OpenAI).create(ApiKey("key"))

        assertIs<KoogLlmChat>(anthropicChat)
        assertIs<KoogLlmChat>(openAiChat)
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
