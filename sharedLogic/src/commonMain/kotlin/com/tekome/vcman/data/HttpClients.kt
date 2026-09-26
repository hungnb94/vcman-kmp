package com.tekome.vcman.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 30_000L

private val defaultHttpClientConfig: HttpClientConfig<*>.() -> Unit = {
    expectSuccess = true
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = DEFAULT_REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = DEFAULT_REQUEST_TIMEOUT_MILLIS
    }
}

internal fun createDefaultHttpClient(): HttpClient = HttpClient(block = defaultHttpClientConfig)

internal fun createHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine, defaultHttpClientConfig)

private val defaultHttpClient: HttpClient by lazy { createDefaultHttpClient() }

internal fun defaultSearchToolResolver(http: HttpClient = defaultHttpClient): (WebSearchToolConfig) -> WebSearchTool =
    { config -> config.createTool(http) }
