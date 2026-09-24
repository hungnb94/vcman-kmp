package com.tekome.vcman.data

import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpClientsTest {
    @Serializable
    private class Payload(
        val known: String,
    )

    @Test
    fun createHttpClient_ignoresUnknownJsonKeysWhenDecoding() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        """{"known":"value","unknown":"extra"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = createHttpClient(engine)

            val payload: Payload = client.get("https://example.invalid").body()

            assertEquals("value", payload.known)
        }

    @Test
    fun createHttpClient_non2xxResponseThrowsResponseException() =
        runTest {
            val engine = MockEngine { respond("nope", HttpStatusCode.Unauthorized) }
            val client = createHttpClient(engine)

            assertFailsWith<ResponseException> { client.get("https://example.invalid") }
        }
}
