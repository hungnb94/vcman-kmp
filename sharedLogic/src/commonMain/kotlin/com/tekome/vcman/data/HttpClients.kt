package com.tekome.vcman.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 30_000L

/**
 * Single source of truth for the client config [createDefaultHttpClient] and [createHttpClient]
 * both apply, so tests exercising a [io.ktor.client.engine.mock.MockEngine] via [createHttpClient]
 * observe the exact same `ignoreUnknownKeys`/timeout/`expectSuccess` settings production traffic
 * gets — no hand-mirrored duplicate to drift out of sync.
 * `expectSuccess = true` makes a 4xx/5xx response throw Ktor's [io.ktor.client.plugins.ResponseException],
 * which [errorRules] classifies as [AnalysisError.ApiError].
 */
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

/**
 * Ktor selects the engine per target (OkHttp/Darwin/JS) automatically from the engine artifact on
 * the classpath, so no `expect`/`actual` is needed here.
 */
internal fun createDefaultHttpClient(): HttpClient = HttpClient(block = defaultHttpClientConfig)

/**
 * Same config as [createDefaultHttpClient], with an explicit [engine] — the extension point tests
 * use to inject a [io.ktor.client.engine.mock.MockEngine] without re-declaring the config.
 */
internal fun createHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine, defaultHttpClientConfig)

/**
 * Binds [WebSearchToolConfig.createTool] to a concrete [HttpClient], exposed as a plain function
 * so `KoogScoreAnalysisService.kt` never needs to import `io.ktor.*` itself (see dependency
 * direction in the implementation plan): it only sees `(WebSearchToolConfig) -> WebSearchTool`.
 */
internal fun defaultSearchToolResolver(http: HttpClient = createDefaultHttpClient()): (WebSearchToolConfig) -> WebSearchTool =
    { config -> config.createTool(http) }
