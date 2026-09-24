package com.tekome.vcman.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BraveSearchToolTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    /** Uses the real production client factory so tests decode exactly like [BraveSearchTool] does at runtime. */
    private fun testHttpClient(handler: MockRequestHandler): HttpClient = createHttpClient(MockEngine(handler))

    private fun clientReturning(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpClient = testHttpClient { respond(body, status, jsonHeaders) }

    @Test
    fun search_mapsResultsToTitleUrlSnippet() =
        runTest {
            val body = """{"web":{"results":[{"title":"A","url":"https://a.example","description":"snippet-a"}]}}"""
            val tool = BraveSearchTool(clientReturning(body), ApiKey("key"))

            val results = tool.search("query")

            assertEquals(listOf(WebSearchResult("A", "https://a.example", "snippet-a")), results)
        }

    @Test
    fun search_dropsItemsMissingTitleOrUrl() =
        runTest {
            val body =
                """
                {"web":{"results":[
                    {"title":"A","url":"https://a.example","description":"d"},
                    {"title":null,"url":"https://b.example","description":"d"},
                    {"title":"C","url":null,"description":"d"}
                ]}}
                """.trimIndent()
            val tool = BraveSearchTool(clientReturning(body), ApiKey("key"))

            val results = tool.search("query")

            assertEquals(1, results.size)
            assertEquals("A", results.single().title)
        }

    @Test
    fun search_sendsApiKeyInHeaderNotUrl() =
        runTest {
            var capturedHeader: String? = null
            var capturedUrl: String? = null
            val http =
                testHttpClient { request ->
                    capturedHeader = request.headers["X-Subscription-Token"]
                    capturedUrl = request.url.toString()
                    respond("""{"web":{"results":[]}}""", HttpStatusCode.OK, jsonHeaders)
                }
            val tool = BraveSearchTool(http, ApiKey("brave-secret"))

            tool.search("query")

            assertEquals("brave-secret", capturedHeader)
            assertTrue("brave-secret" !in (capturedUrl ?: ""))
        }

    @Test
    fun search_sendsQueryParameter() =
        runTest {
            var capturedQuery: String? = null
            val http =
                testHttpClient { request ->
                    capturedQuery = request.url.parameters["q"]
                    respond("""{"web":{"results":[]}}""", HttpStatusCode.OK, jsonHeaders)
                }
            val tool = BraveSearchTool(http, ApiKey("key"))

            tool.search("vc rubric scoring")

            assertEquals("vc rubric scoring", capturedQuery)
        }

    @Test
    fun search_http401ThrowsResponseException() =
        runTest {
            val tool = BraveSearchTool(clientReturning("unauthorized", HttpStatusCode.Unauthorized), ApiKey("key"))

            assertFailsWith<ResponseException> { tool.search("query") }
        }

    @Test
    fun search_emptyPayloadReturnsEmptyList() =
        runTest {
            val tool = BraveSearchTool(clientReturning("""{"web":{"results":[]}}"""), ApiKey("key"))

            assertEquals(emptyList(), tool.search("query"))
        }
}
