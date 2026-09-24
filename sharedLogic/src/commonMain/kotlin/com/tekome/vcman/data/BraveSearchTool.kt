package com.tekome.vcman.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

/** Brave Search API (`res/v1/web/search`) backed [WebSearchTool]. */
internal class BraveSearchTool(
    private val http: HttpClient,
    private val apiKey: ApiKey,
) : WebSearchTool {
    override suspend fun search(query: String): List<WebSearchResult> {
        val response: BraveSearchResponse =
            http
                .get(BRAVE_SEARCH_URL) {
                    parameter("q", query)
                    // The API key is a header value only; it must never end up in the URL/query string.
                    header("X-Subscription-Token", apiKey.value)
                }.body()
        return response.web
            ?.results
            .orEmpty()
            .mapNotNull { it.toWebSearchResult() }
    }

    private companion object {
        const val BRAVE_SEARCH_URL = "https://api.search.brave.com/res/v1/web/search"
    }
}

@Serializable
private data class BraveSearchResponse(
    val web: Web? = null,
) {
    @Serializable
    data class Web(
        val results: List<Item> = emptyList(),
    )

    @Serializable
    data class Item(
        val title: String? = null,
        val url: String? = null,
        val description: String? = null,
    )
}

/** Drops entries missing a title or url; a snippet-less result is still usable, so it defaults to empty. */
private fun BraveSearchResponse.Item.toWebSearchResult(): WebSearchResult? {
    val resultTitle = title?.takeIf { it.isNotBlank() } ?: return null
    val resultUrl = url?.takeIf { it.isNotBlank() } ?: return null
    return WebSearchResult(title = resultTitle, url = resultUrl, snippet = description.orEmpty())
}
