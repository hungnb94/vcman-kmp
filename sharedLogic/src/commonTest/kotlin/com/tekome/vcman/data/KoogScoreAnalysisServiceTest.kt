package com.tekome.vcman.data

import com.tekome.vcman.domain.RubricInput
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class KoogScoreAnalysisServiceTest {
    private val rubric = RubricInput(title = "Rubric", text = "Text")

    private val fakePromptBuilder =
        object : PromptBuilder {
            override fun buildSystemPrompt(rubric: RubricInput): String = "SYSTEM:${rubric.title}"

            override fun buildUserPrompt(subjectQuery: String): String = "USER:$subjectQuery"
        }

    private val validEnvelope =
        """
        {"subjectName":"Acme","overallSummary":"Great fit","sections":[
          {"name":"Team","questions":[
            {"id":"q1","label":"Founder","weight":2.0,"rawScore":8.0,"comment":"Strong","sourceUrl":"https://example.com"}
          ]}
        ]}
        """.trimIndent()

    private fun serviceWith(
        resolver: LlmChatResolver,
        searchToolResolver: (WebSearchToolConfig) -> WebSearchTool = defaultSearchToolResolver(HttpClient(MockEngine { respondOk() })),
        nowMillis: () -> Long = { 42L },
    ) = KoogScoreAnalysisService(
        llmChatResolver = resolver,
        searchToolResolver = searchToolResolver,
        promptBuilder = fakePromptBuilder,
        nowMillis = nowMillis,
    )

    private fun requestConfig(
        provider: LlmProvider = LlmProvider.Anthropic,
        searchTool: WebSearchToolConfig? = null,
    ) = LlmRequestConfig(provider = provider, apiKey = ApiKey("key"), searchTool = searchTool)

    @Test
    fun analyze_validEnvelopeReturnsMappedReport() =
        runTest {
            val chat = LlmChat { _, _, _ -> validEnvelope }
            val service = serviceWith({ LlmChatFactory { chat } }, nowMillis = { 999L })

            val result = service.analyze(rubric, "Acme Inc", requestConfig())

            val report = result.getOrThrow()
            assertEquals("Acme", report.subjectName)
            assertEquals("Rubric", report.rubricTitle)
            assertEquals(999L, report.generatedAtEpochMillis)
            val question =
                report.sections
                    .single()
                    .questions
                    .single()
            assertEquals("q1", question.id)
            assertEquals("https://example.com", question.sourceUrl)
        }

    @Test
    fun analyze_emptySectionsReturnsAmbiguousSubject() =
        runTest {
            val raw = """{"subjectName":"X","overallSummary":"Ambiguous: Foo Inc vs Foo Corp","sections":[]}"""
            val chat = LlmChat { _, _, _ -> raw }
            val service = serviceWith({ LlmChatFactory { chat } })

            val result = service.analyze(rubric, "Foo", requestConfig())

            val exception = assertIs<AnalysisException>(result.exceptionOrNull())
            val error = assertIs<AnalysisError.AmbiguousSubject>(exception.error)
            assertEquals("Ambiguous: Foo Inc vs Foo Corp", error.explanation)
        }

    @Test
    fun analyze_chatThrowsIOExceptionReturnsNetwork() =
        runTest {
            val chat = LlmChat { _, _, _ -> throw IOException("connection refused") }
            val service = serviceWith({ LlmChatFactory { chat } })

            val result = service.analyze(rubric, "Acme", requestConfig())

            val exception = assertIs<AnalysisException>(result.exceptionOrNull())
            assertEquals(AnalysisError.Network, exception.error)
        }

    @Test
    fun analyze_chatThrowsResponseExceptionReturnsApiError() =
        runTest {
            val unauthorizedClient =
                HttpClient(MockEngine { respond("nope", HttpStatusCode.Unauthorized) }) {
                    expectSuccess = true
                }
            val chat =
                LlmChat { _, _, _ ->
                    unauthorizedClient.get("https://example.invalid/v1/messages")
                    error("unreachable")
                }
            val service = serviceWith({ LlmChatFactory { chat } })

            val result = service.analyze(rubric, "Acme", requestConfig())

            val exception = assertIs<AnalysisException>(result.exceptionOrNull())
            assertEquals(AnalysisError.ApiError(401), exception.error)
        }

    @Test
    fun analyze_garbageResponseReturnsInvalidResponse() =
        runTest {
            val chat = LlmChat { _, _, _ -> "not json at all" }
            val service = serviceWith({ LlmChatFactory { chat } })

            val result = service.analyze(rubric, "Acme", requestConfig())

            assertIs<AnalysisError.InvalidResponse>(assertIs<AnalysisException>(result.exceptionOrNull()).error)
        }

    @Test
    fun analyze_cancellationIsRethrownNotWrapped() =
        runTest {
            val chat = LlmChat { _, _, _ -> throw CancellationException("cancelled") }
            val service = serviceWith({ LlmChatFactory { chat } })

            assertFailsWith<CancellationException> {
                service.analyze(rubric, "Acme", requestConfig())
            }
        }

    @Test
    fun analyze_switchingProviderOnlyChangesResolvedFactory() =
        runTest {
            val requestedProviders = mutableListOf<LlmProvider>()
            val chat = LlmChat { _, _, _ -> validEnvelope }
            val resolver: LlmChatResolver = { provider ->
                requestedProviders += provider
                LlmChatFactory { chat }
            }
            val service = serviceWith(resolver)

            service.analyze(rubric, "Acme", requestConfig(LlmProvider.Anthropic))
            service.analyze(rubric, "Acme", requestConfig(LlmProvider.OpenAI))

            assertEquals(listOf(LlmProvider.Anthropic, LlmProvider.OpenAI), requestedProviders)
        }

    @Test
    fun analyze_nullSearchToolPassesNoTools() =
        runTest {
            var capturedTools: List<WebSearchTool>? = null
            val chat =
                LlmChat { _, _, tools ->
                    capturedTools = tools
                    validEnvelope
                }
            val service = serviceWith({ LlmChatFactory { chat } })

            service.analyze(rubric, "Acme", requestConfig(searchTool = null))

            assertEquals(emptyList(), capturedTools)
        }

    @Test
    fun analyze_braveOrFirecrawlConfigPassesMatchingTool() =
        runTest {
            var capturedTools: List<WebSearchTool>? = null
            val chat =
                LlmChat { _, _, tools ->
                    capturedTools = tools
                    validEnvelope
                }
            val service = serviceWith({ LlmChatFactory { chat } })

            service.analyze(rubric, "Acme", requestConfig(searchTool = WebSearchToolConfig.Brave(ApiKey("brave-key"))))

            assertEquals(1, capturedTools?.size)
            assertIs<BraveSearchTool>(capturedTools!!.first())
        }

    @Test
    fun analyze_firecrawlConfigPassesMatchingTool() =
        runTest {
            var capturedTools: List<WebSearchTool>? = null
            val chat =
                LlmChat { _, _, tools ->
                    capturedTools = tools
                    validEnvelope
                }
            val service = serviceWith({ LlmChatFactory { chat } })

            service.analyze(
                rubric,
                "Acme",
                requestConfig(searchTool = WebSearchToolConfig.Firecrawl(ApiKey("firecrawl-key"))),
            )

            assertEquals(1, capturedTools?.size)
            assertIs<FirecrawlSearchTool>(capturedTools!!.first())
        }

    @Test
    fun analyze_passesPromptBuilderOutputVerbatim() =
        runTest {
            var capturedSystem: String? = null
            var capturedUser: String? = null
            val chat =
                LlmChat { system, user, _ ->
                    capturedSystem = system
                    capturedUser = user
                    validEnvelope
                }
            val service = serviceWith({ LlmChatFactory { chat } })

            service.analyze(rubric, "Acme Query", requestConfig())

            assertEquals("SYSTEM:Rubric", capturedSystem)
            assertEquals("USER:Acme Query", capturedUser)
        }
}
