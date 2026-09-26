package com.tekome.vcman.data

import io.ktor.client.HttpClient

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

fun interface WebSearchTool {
    suspend fun search(query: String): List<WebSearchResult>
}

sealed class WebSearchToolConfig {
    internal abstract fun createTool(http: HttpClient): WebSearchTool

    class Firecrawl(
        val apiKey: ApiKey,
    ) : WebSearchToolConfig() {
        override fun createTool(http: HttpClient): WebSearchTool = FirecrawlSearchTool(http, apiKey)

        override fun toString(): String = "WebSearchToolConfig.Firecrawl"
    }
}
