package com.tekome.vcman.data

import io.ktor.client.HttpClient

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

/**
 * Throws [AnalysisException] with [AnalysisError.WebSearchRejected] when the key is refused, and
 * [WebSearchUnavailableException] for anything the analysis should continue without.
 */
fun interface WebSearchTool {
    suspend fun search(query: String): List<WebSearchResult>
}

class WebSearchUnavailableException(
    val httpStatus: Int?,
    cause: Throwable? = null,
) : Exception("Web search unavailable (HTTP $httpStatus)", cause)

sealed class WebSearchToolConfig {
    internal abstract fun createTool(http: HttpClient): WebSearchTool

    class Firecrawl(
        val apiKey: ApiKey,
    ) : WebSearchToolConfig() {
        override fun createTool(http: HttpClient): WebSearchTool = FirecrawlSearchTool(http, apiKey)

        override fun toString(): String = "WebSearchToolConfig.Firecrawl"
    }
}
