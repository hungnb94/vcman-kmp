package com.tekome.vcman.data

import kotlin.test.Test
import kotlin.test.assertFalse

class ScoreAnalysisServiceTest {
    @Test
    fun apiKey_toStringIsRedacted() {
        val key = ApiKey("sk-super-secret-123")

        assertFalse("sk-super-secret-123" in key.toString())
    }

    @Test
    fun llmRequestConfig_toStringNeverContainsApiKey() {
        val config =
            LlmRequestConfig(
                settings = validSettings(LlmProviderType.OpenAICompatible, key = "sk-super-secret-123"),
                searchTool = WebSearchToolConfig.Firecrawl(ApiKey("firecrawl-secret-456")),
            )

        val rendered = "$config ${config.settings} ${config.searchTool}"

        assertFalse("sk-super-secret-123" in rendered)
        assertFalse("firecrawl-secret-456" in rendered)
    }
}
