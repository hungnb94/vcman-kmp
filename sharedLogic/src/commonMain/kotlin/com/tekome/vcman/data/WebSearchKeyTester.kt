package com.tekome.vcman.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException

fun interface WebSearchKeyTester {
    suspend fun test(key: ApiKey): ConnectionTestResult
}

private const val HTTP_PAYMENT_REQUIRED = 402

/** Checks the key against Firecrawl's account endpoint, which does not spend search credits. */
class FirecrawlKeyTester internal constructor(
    private val http: HttpClient,
) : WebSearchKeyTester {
    constructor() : this(defaultHttpClient)

    override suspend fun test(key: ApiKey): ConnectionTestResult =
        try {
            http.get(CREDIT_USAGE_URL) { header(HttpHeaders.Authorization, "Bearer ${key.value.trim()}") }
            ConnectionTestResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when (val failure = firecrawlFailureOf(e)) {
                is WebSearchFailure.Rejected ->
                    if (failure.httpStatus == HTTP_PAYMENT_REQUIRED) {
                        ConnectionTestResult.Api(failure.httpStatus)
                    } else {
                        ConnectionTestResult.Unauthorized
                    }

                is WebSearchFailure.Unavailable ->
                    failure.httpStatus?.let { ConnectionTestResult.Api(it) } ?: ConnectionTestResult.Network
            }
        }

    private companion object {
        const val CREDIT_USAGE_URL = "https://api.firecrawl.dev/v2/team/credit-usage"
    }
}
