package com.tekome.vcman.data

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
}
