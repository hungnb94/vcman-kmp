package com.tekome.vcman.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FirecrawlSearchToolTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    /** Uses the real production client factory so tests decode exactly like [FirecrawlSearchTool] does at runtime. */
    private fun testHttpClient(handler: MockRequestHandler): HttpClient = createHttpClient(MockEngine(handler))

    private fun clientReturning(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpClient = testHttpClient { respond(body, status, jsonHeaders) }

    @Test
    fun search_mapsResultsToTitleUrlSnippet() =
        runTest {
            val body = """{"success":true,"data":[{"title":"A","url":"https://a.example","description":"snippet-a"}]}"""
            val tool = FirecrawlSearchTool(clientReturning(body), ApiKey("key"))

            val results = tool.search("query")

            assertEquals(listOf(WebSearchResult("A", "https://a.example", "snippet-a")), results)
        }

    @Test
    fun search_dropsItemsMissingTitleOrUrl() =
        runTest {
            val body =
                """
                {"success":true,"data":[
                    {"title":"A","url":"https://a.example","description":"d"},
                    {"title":null,"url":"https://b.example","description":"d"}
                ]}
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
                    respond("""{"success":true,"data":[]}""", HttpStatusCode.OK, jsonHeaders)
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
                    respond("""{"success":true,"data":[]}""", HttpStatusCode.OK, jsonHeaders)
                }
            val tool = FirecrawlSearchTool(http, ApiKey("key"))

            tool.search("vc rubric scoring")

            assertTrue(capturedBody?.contains("vc rubric scoring") == true)
        }

    @Test
    fun search_http401ThrowsResponseException() =
        runTest {
            val tool = FirecrawlSearchTool(clientReturning("unauthorized", HttpStatusCode.Unauthorized), ApiKey("key"))

            assertFailsWith<ResponseException> { tool.search("query") }
        }

    @Test
    fun search_emptyPayloadReturnsEmptyList() =
        runTest {
            val tool = FirecrawlSearchTool(clientReturning("""{"success":true,"data":[]}"""), ApiKey("key"))

            assertEquals(emptyList(), tool.search("query"))
        }
}
