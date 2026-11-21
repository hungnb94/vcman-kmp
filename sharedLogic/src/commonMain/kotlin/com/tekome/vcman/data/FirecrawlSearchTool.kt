package com.tekome.vcman.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.Serializable

internal class FirecrawlSearchTool(
    private val http: HttpClient,
    private val apiKey: ApiKey,
) : WebSearchTool {
    override suspend fun search(query: String): List<WebSearchResult> {
        val response: FirecrawlSearchResponse =
            try {
                http
                    .post(FIRECRAWL_SEARCH_URL) {
                        contentType(ContentType.Application.Json)
                        header("Authorization", "Bearer ${apiKey.value}")
                        setBody(FirecrawlSearchRequest(query = query, limit = SEARCH_RESULT_LIMIT))
                    }.body()
            } catch (e: TimeoutCancellationException) {
                throw WebSearchUnavailableException(httpStatus = null, cause = e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw e.toSearchException()
            }
        if (!response.success) throw WebSearchUnavailableException(httpStatus = null)
        return response.data?.web.orEmpty().mapNotNull { it.toWebSearchResult() }
    }

    private companion object {
        const val FIRECRAWL_SEARCH_URL = "https://api.firecrawl.dev/v2/search"

        // v2 raised the default from 5 to 10 at the same 2 credits; 5 keeps prompts short.
        const val SEARCH_RESULT_LIMIT = 5
    }
}

@Serializable
private data class FirecrawlSearchRequest(
    val query: String,
    val limit: Int,
)

@Serializable
private data class FirecrawlSearchResponse(
    val success: Boolean = true,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val web: List<Item>? = null,
    )

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

private fun Exception.toSearchException(): Exception =
    when (val failure = firecrawlFailureOf(this)) {
        is WebSearchFailure.Rejected -> AnalysisException(AnalysisError.WebSearchRejected(failure.httpStatus), this)
        is WebSearchFailure.Unavailable -> WebSearchUnavailableException(failure.httpStatus, this)
    }
