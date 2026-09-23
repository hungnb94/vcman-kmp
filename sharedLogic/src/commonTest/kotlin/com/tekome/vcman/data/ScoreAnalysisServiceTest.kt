package com.tekome.vcman.data

import kotlin.test.Test
import kotlin.test.assertEquals
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
                provider = LlmProvider.OpenAI,
                apiKey = ApiKey("sk-super-secret-123"),
                searchTool = WebSearchToolConfig.Brave(ApiKey("brave-secret-456")),
            )

        val rendered = "$config ${config.apiKey} ${config.searchTool}"

        assertFalse("sk-super-secret-123" in rendered)
        assertFalse("brave-secret-456" in rendered)
    }

    @Test
    fun llmProvider_displayNameIsDefinedForEveryShippedProvider() {
        assertEquals("Anthropic", LlmProvider.Anthropic.displayName)
        assertEquals("OpenAI", LlmProvider.OpenAI.displayName)
    }
}
