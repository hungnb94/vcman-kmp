package com.tekome.vcman.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

internal class FirecrawlSearchTool(
    private val http: HttpClient,
    private val apiKey: ApiKey,
) : WebSearchTool {
    override suspend fun search(query: String): List<WebSearchResult> {
        val response: FirecrawlSearchResponse =
            http
                .post(FIRECRAWL_SEARCH_URL) {
                    contentType(ContentType.Application.Json)
                    // The API key is a header value only; it must never end up in the URL/query string.
                    header("Authorization", "Bearer ${apiKey.value}")
                    setBody(FirecrawlSearchRequest(query = query))
                }.body()
        return response.data.orEmpty().mapNotNull { it.toWebSearchResult() }
    }

    private companion object {
        const val FIRECRAWL_SEARCH_URL = "https://api.firecrawl.dev/v1/search"
    }
}

@Serializable
private data class FirecrawlSearchRequest(
    val query: String,
)

@Serializable
private data class FirecrawlSearchResponse(
    val data: List<Item>? = null,
) {
    @Serializable
    data class Item(
        val title: String? = null,
        val url: String? = null,
        val description: String? = null,
    )
}

private fun FirecrawlSearchResponse.Item.toWebSearchResult(): WebSearchResult? {
    val resultTitle = title?.takeIf { it.isNotBlank() } ?: return null
    val resultUrl = url?.takeIf { it.isNotBlank() } ?: return null
    return WebSearchResult(title = resultTitle, url = resultUrl, snippet = description.orEmpty())
}
