package com.tekome.vcman.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

class WebSearchToolTest {
    private fun noopHttpClient(): HttpClient = HttpClient(MockEngine { respondOk() })

    @Test
    fun webSearchToolConfig_toStringNeverContainsApiKey() {
        val firecrawl = WebSearchToolConfig.Firecrawl(ApiKey("firecrawl-secret"))

        assertFalse("firecrawl-secret" in firecrawl.toString())
    }

    @Test
    fun createTool_firecrawlConfigCreatesFirecrawlSearchTool() {
        val tool = WebSearchToolConfig.Firecrawl(ApiKey("key")).createTool(noopHttpClient())

        assertIs<FirecrawlSearchTool>(tool)
    }
}
