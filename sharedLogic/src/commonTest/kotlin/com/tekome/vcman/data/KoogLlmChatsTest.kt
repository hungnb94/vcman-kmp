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

            assertEquals("Title acme | https://example.com/acme | snippet", output)
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
}
