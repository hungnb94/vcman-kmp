package com.tekome.vcman.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FirecrawlKeyTesterTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun tester(handler: MockRequestHandler) = FirecrawlKeyTester(createHttpClient(MockEngine(handler)))

    private fun testerReturning(status: HttpStatusCode) = tester { respond("""{"success":false}""", status, jsonHeaders) }

    @Test
    fun callsCreditUsageWithTrimmedBearerKey() =
        runTest {
            var method: HttpMethod? = null
            var url: String? = null
            var auth: String? = null
            val tester =
                tester { request ->
                    method = request.method
                    url = request.url.toString()
                    auth = request.headers[HttpHeaders.Authorization]
                    respond("""{"success":true,"data":{"remainingCredits":500}}""", HttpStatusCode.OK, jsonHeaders)
                }

            val result = tester.test(ApiKey("  fc-key  "))

            assertEquals(ConnectionTestResult.Success, result)
            assertEquals(HttpMethod.Get, method)
            assertEquals("https://api.firecrawl.dev/v2/team/credit-usage", url)
            assertEquals("Bearer fc-key", auth)
        }

    @Test
    fun mapsStatusesToConnectionTestResults() =
        runTest {
            val cases =
                mapOf(
                    HttpStatusCode.Unauthorized to ConnectionTestResult.Unauthorized,
                    HttpStatusCode.Forbidden to ConnectionTestResult.Unauthorized,
                    HttpStatusCode.PaymentRequired to ConnectionTestResult.Api(402),
                    HttpStatusCode.TooManyRequests to ConnectionTestResult.Api(429),
                    HttpStatusCode.InternalServerError to ConnectionTestResult.Api(500),
                )

            cases.forEach { (status, expected) ->
                assertEquals(expected, testerReturning(status).test(ApiKey("fc")), "status $status")
            }
        }

    @Test
    fun networkFailureIsNetwork() =
        runTest {
            assertEquals(ConnectionTestResult.Network, tester { throw IOException("offline") }.test(ApiKey("fc")))
        }

    @Test
    fun cancellationIsRethrown() =
        runTest {
            assertFailsWith<CancellationException> { tester { throw CancellationException("gone") }.test(ApiKey("fc")) }
        }
}
