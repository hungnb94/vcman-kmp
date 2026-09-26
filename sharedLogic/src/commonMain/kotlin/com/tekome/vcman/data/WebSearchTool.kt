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

/**
 * Chooses a web-search backend and holds its credentials. Each case creates its own
 * [WebSearchTool]; adding a new backend never requires touching an existing case, the Koog tool
 * adapter, or [ScoreAnalysisService] (OCP via polymorphism instead of a `when` on backend type).
 *
 * A `sealed class` (not `sealed interface`) so [createTool] can stay `internal`: the [HttpClient]
 * type it needs must not leak into this module's public API, and therefore not into the generated
 * TypeScript definitions for the `js` target.
 */
sealed class WebSearchToolConfig {
    internal abstract fun createTool(http: HttpClient): WebSearchTool

    class Firecrawl(
        val apiKey: ApiKey,
    ) : WebSearchToolConfig() {
        override fun createTool(http: HttpClient): WebSearchTool = FirecrawlSearchTool(http, apiKey)

        override fun toString(): String = "WebSearchToolConfig.Firecrawl"
    }
}
