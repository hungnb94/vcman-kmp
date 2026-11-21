package com.tekome.vcman.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FirecrawlSearchToolTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun testHttpClient(handler: MockRequestHandler): HttpClient = createHttpClient(MockEngine(handler))

    private fun clientReturning(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpClient = testHttpClient { respond(body, status, jsonHeaders) }

    @Test
    fun search_mapsResultsToTitleUrlSnippet() =
        runTest {
            val body = """{"success":true,"data":{"web":[{"title":"A","url":"https://a.example","description":"snippet-a"}]}}"""
            val tool = FirecrawlSearchTool(clientReturning(body), ApiKey("key"))

            val results = tool.search("query")

            assertEquals(listOf(WebSearchResult("A", "https://a.example", "snippet-a")), results)
        }

    @Test
    fun search_dropsItemsMissingTitleOrUrl() =
        runTest {
            val body =
                """
                {"success":true,"data":{"web":[
                    {"title":"A","url":"https://a.example","description":"d"},
                    {"title":null,"url":"https://b.example","description":"d"}
                ]}}
                """.trimIndent()
            val tool = FirecrawlSearchTool(clientReturning(body), ApiKey("key"))

            val results = tool.search("query")

            assertEquals(1, results.size)
        }

    @Test
    fun search_sendsApiKeyInHeaderNotUrl() =
        runTest {
            var capturedAuth: String? = null
            val http =
                testHttpClient { request ->
                    capturedAuth = request.headers[HttpHeaders.Authorization]
                    respond("""{"success":true,"data":{"web":[]}}""", HttpStatusCode.OK, jsonHeaders)
                }
            val tool = FirecrawlSearchTool(http, ApiKey("firecrawl-secret"))

            tool.search("query")

            assertEquals("Bearer firecrawl-secret", capturedAuth)
        }

    @Test
    fun search_sendsQueryInBody() =
        runTest {
            var capturedBody: String? = null
            val http =
                testHttpClient { request ->
                    capturedBody = request.body.toByteArray().decodeToString()
                    respond("""{"success":true,"data":{"web":[]}}""", HttpStatusCode.OK, jsonHeaders)
                }
            val tool = FirecrawlSearchTool(http, ApiKey("key"))

            tool.search("vc rubric scoring")

            assertEquals(capturedBody?.contains("vc rubric scoring"), true)
        }

    @Test
    fun search_postsToV2WithResultLimit() =
        runTest {
            var capturedUrl: String? = null
            var capturedBody: String? = null
            val http =
                testHttpClient { request ->
                    capturedUrl = request.url.toString()
                    capturedBody = request.body.toByteArray().decodeToString()
                    respond("""{"success":true,"data":{"web":[]}}""", HttpStatusCode.OK, jsonHeaders)
                }

            FirecrawlSearchTool(http, ApiKey("key")).search("query")

            assertEquals("https://api.firecrawl.dev/v2/search", capturedUrl)
            assertEquals(capturedBody?.contains("\"limit\":5"), true)
        }

    @Test
    fun search_missingWebListReturnsEmptyList() =
        runTest {
            val tool = FirecrawlSearchTool(clientReturning("""{"success":true,"data":{"news":[]}}"""), ApiKey("key"))

            assertEquals(emptyList(), tool.search("query"))
        }

    @Test
    fun search_keyRefusedStatusesThrowWebSearchRejected() =
        runTest {
            listOf(HttpStatusCode.Unauthorized, HttpStatusCode.PaymentRequired, HttpStatusCode.Forbidden).forEach { status ->
                val tool = FirecrawlSearchTool(clientReturning("""{"success":false}""", status), ApiKey("key"))

                val error = assertFailsWith<AnalysisException> { tool.search("query") }

                assertEquals(AnalysisError.WebSearchRejected(status.value), error.error)
            }
        }

    @Test
    fun search_transientStatusesThrowUnavailableWithStatus() =
        runTest {
            listOf(
                HttpStatusCode.BadRequest,
                HttpStatusCode.NotFound,
                HttpStatusCode.Gone,
                HttpStatusCode.RequestTimeout,
                HttpStatusCode.TooManyRequests,
                HttpStatusCode.InternalServerError,
                HttpStatusCode.BadGateway,
            ).forEach { status ->
                val tool = FirecrawlSearchTool(clientReturning("""{"success":false}""", status), ApiKey("key"))

                val error = assertFailsWith<WebSearchUnavailableException> { tool.search("query") }

                assertEquals(status.value, error.httpStatus)
            }
        }

    @Test
    fun search_networkFailureThrowsUnavailableWithoutStatus() =
        runTest {
            val tool = FirecrawlSearchTool(testHttpClient { throw IOException("offline") }, ApiKey("key"))

            val error = assertFailsWith<WebSearchUnavailableException> { tool.search("query") }

            assertNull(error.httpStatus)
        }

    @Test
    fun search_cancellationIsRethrown() =
        runTest {
            val tool = FirecrawlSearchTool(testHttpClient { throw CancellationException("cancelled") }, ApiKey("key"))

            assertFailsWith<CancellationException> { tool.search("query") }
        }

    @Test
    fun search_emptyPayloadReturnsEmptyList() =
        runTest {
            val tool = FirecrawlSearchTool(clientReturning("""{"success":true,"data":{"web":[]}}"""), ApiKey("key"))

            assertEquals(emptyList(), tool.search("query"))
        }

    @Test
    fun search_httpOkButSuccessFalseThrowsUnavailable() =
        runTest {
            val tool = FirecrawlSearchTool(clientReturning("""{"success":false}"""), ApiKey("key"))

            assertFailsWith<WebSearchUnavailableException> { tool.search("query") }
        }
}
