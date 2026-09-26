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
        // Constructing a client must not perform any network I/O, so this is safe without a key.
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
        // The agent hitting its iteration cap without finishing (e.g. MAX_AGENT_ITERATIONS with a
        // slow-converging tool loop) must not leak a raw Koog exception out of `analyze` — it should
        // classify the same way any other "LLM never produced a usable answer" failure does.
        val exception = AIAgentMaxNumberOfIterationsReachedException(20)

        val error = classify(exception, koogErrorRules)

        assertIs<AnalysisError.InvalidResponse>(error)
    }

    @Test
    fun koogErrorRules_classifiesKoogHttpClientExceptionAsApiError() {
        // The Ktor-backed Koog HTTP client (see KtorKoogHttpClient) reports every non-2xx LLM
        // provider response as a KoogHttpClientException, not Ktor's own ResponseException, so this
        // rule is the only thing standing between a 401/429/500 from Anthropic/OpenAI and a raw,
        // unclassified exception leaking out of `analyze`.
        val exception = KoogHttpClientException(clientName = "anthropic", statusCode = 429)

        val error = classify(exception, koogErrorRules)

        assertEquals(AnalysisError.ApiError(429), error)
    }
}
