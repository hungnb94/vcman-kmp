package com.tekome.vcman.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WebSearchSettingsTest {
    @Test
    fun blankKeyMeansNoSearchTool() {
        assertNull(WebSearchSettings.Off.toolConfig)
        assertNull(WebSearchSettings(ApiKey("   ")).toolConfig)
    }

    @Test
    fun keyIsTrimmedIntoAFirecrawlConfig() {
        val config = assertIs<WebSearchToolConfig.Firecrawl>(WebSearchSettings(ApiKey("  fc-1  ")).toolConfig)

        assertEquals("fc-1", config.apiKey.value)
    }

    @Test
    fun toStringNeverShowsTheKey() {
        assertEquals(false, WebSearchSettings(ApiKey("fc-secret")).toString().contains("fc-secret"))
    }
}
