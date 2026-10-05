package com.tekome.vcman.presentation

import com.tekome.vcman.data.AnalysisError
import com.tekome.vcman.data.AnalysisException
import com.tekome.vcman.data.LlmProviderType
import com.tekome.vcman.data.LlmRequestConfig
import com.tekome.vcman.data.ScoreAnalysisService
import com.tekome.vcman.domain.LanguageTag
import com.tekome.vcman.domain.ProjectScoreReport
import com.tekome.vcman.domain.RubricInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

private val VI = LanguageTag.parse("vi")

@OptIn(ExperimentalCoroutinesApi::class)
class ScoreAnalysisViewModelTest {
    /** Records every call and defers to [pending] so a test can hold the job in `Loading`. */
    private class FakeScoreAnalysisService(
        private val pending: CompletableDeferred<Result<ProjectScoreReport>> = CompletableDeferred(),
    ) : ScoreAnalysisService {
        var calls = 0
            private set
        var lastRubric: RubricInput? = null
            private set
        var lastSubjectQuery: String? = null
            private set
        var lastConfig: LlmRequestConfig? = null
            private set
        var lastOutputLanguage: LanguageTag? = null
            private set

        override suspend fun analyze(
            rubric: RubricInput,
            subjectQuery: String,
            config: LlmRequestConfig,
            outputLanguage: LanguageTag,
        ): Result<ProjectScoreReport> {
            calls++
            lastRubric = rubric
            lastSubjectQuery = subjectQuery
            lastConfig = config
            lastOutputLanguage = outputLanguage
            return pending.await()
        }

        fun complete(result: Result<ProjectScoreReport>) = pending.complete(result)
    }

    private fun sampleReport(): ProjectScoreReport =
        ProjectScoreReport(
            subjectName = "Acme Inc",
            rubricTitle = "Series A rubric",
            overallSummary = "Strong fit",
            sections = emptyList(),
            generatedAtEpochMillis = 1L,
        )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun uiState_initialValueIsIdle() {
        val vm = ScoreAnalysisViewModel(service = FakeScoreAnalysisService())

        assertEquals(AnalysisUiState.Idle, vm.uiState.value)
    }

    @Test
    fun analyze_anyBlankFieldEmitsErrorWithoutCallingService() =
        runTest {
            val blankVariants = listOf("", "   ", "\t")
            for (blank in blankVariants) {
                val service = FakeScoreAnalysisService()
                val vm = ScoreAnalysisViewModel(service = service)

                vm.analyze(blank, "Rubric text", "Acme", "sk-valid-key", VI)

                val state = assertIs<AnalysisUiState.Error>(vm.uiState.value)
                assertEquals(0, service.calls)
                assertEquals(
                    AnalysisFailure.MissingFields(listOf(RequiredFieldId.RubricTitle)),
                    state.failure,
                )
                assertFalse("sk-valid-key" in state.toString())
            }
        }

    @Test
    fun analyze_allFieldsBlankListsEveryMissingField() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)

            vm.analyze("", "  ", "", "", VI)

            val state = assertIs<AnalysisUiState.Error>(vm.uiState.value)
            assertEquals(
                AnalysisFailure.MissingFields(
                    listOf(
                        RequiredFieldId.RubricTitle,
                        RequiredFieldId.RubricText,
                        RequiredFieldId.Subject,
                        RequiredFieldId.ApiKey,
                    ),
                ),
                state.failure,
            )
            assertEquals(0, service.calls)
        }

    @Test
    fun analyze_validInputEmitsLoadingSynchronously() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key", VI)

            assertEquals(AnalysisUiState.Loading, vm.uiState.value)
            assertEquals(1, service.calls)
        }

    @Test
    fun analyze_validInputBuildsAnthropicConfigWithoutSearchTool() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key", VI)

            assertEquals(RubricInput(title = "Title", text = "Rubric text"), service.lastRubric)
            assertEquals("Acme", service.lastSubjectQuery)
            assertEquals(VI, service.lastOutputLanguage)
            val config = service.lastConfig
            assertEquals(LlmProviderType.AnthropicCompatible, config?.settings?.providerType)
            assertNull(config?.searchTool)
            assertEquals("sk-valid-key", config?.settings?.apiKey?.value)
        }

    @Test
    fun analyze_successResultEmitsSuccessWithSameReport() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)
            val report = sampleReport()

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key", VI)
            service.complete(Result.success(report))

            val state = assertIs<AnalysisUiState.Success>(vm.uiState.value)
            assertSame(report, state.report)
        }

    @Test
    fun analyze_analysisExceptionFailureEmitsMappedError() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key", VI)
            service.complete(Result.failure(AnalysisException(AnalysisError.Network)))

            val state = assertIs<AnalysisUiState.Error>(vm.uiState.value)
            assertEquals(AnalysisFailure.Network, state.failure)
        }

    @Test
    fun analyze_unknownFailureEmitsFallbackError() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key", VI)
            service.complete(Result.failure(IllegalStateException("apiKey=sk-123 boom")))

            val state = assertIs<AnalysisUiState.Error>(vm.uiState.value)
            assertEquals(AnalysisFailure.Unexpected, state.failure)
            assertFalse("sk-123" in state.toString())
        }

    @Test
    fun analyze_whileLoadingIsNoOp() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key", VI)
            vm.analyze("Other title", "Other text", "Other", "other-key", VI)

            assertEquals(AnalysisUiState.Loading, vm.uiState.value)
            assertEquals(1, service.calls)
        }

    @Test
    fun reset_duringLoadingCancelsJobSoLateResultCannotOverwriteIdle() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key", VI)
            vm.reset()
            service.complete(Result.success(sampleReport()))

            assertEquals(AnalysisUiState.Idle, vm.uiState.value)
        }

    @Test
    fun reset_fromSuccessReturnsIdle() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)
            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key", VI)
            service.complete(Result.success(sampleReport()))
            assertIs<AnalysisUiState.Success>(vm.uiState.value)

            vm.reset()

            assertEquals(AnalysisUiState.Idle, vm.uiState.value)
        }

    @Test
    fun reset_fromErrorReturnsIdle() =
        runTest {
            val vm = ScoreAnalysisViewModel(service = FakeScoreAnalysisService())
            vm.analyze("", "Rubric text", "Acme", "sk-valid-key", VI)
            assertIs<AnalysisUiState.Error>(vm.uiState.value)

            vm.reset()

            assertEquals(AnalysisUiState.Idle, vm.uiState.value)
        }

    @Test
    fun analyze_afterResetStartsNewRequest() =
        runTest {
            val firstService = FakeScoreAnalysisService()
            val secondService = FakeScoreAnalysisService()
            var current: ScoreAnalysisService = firstService
            val delegatingService =
                object : ScoreAnalysisService {
                    override suspend fun analyze(
                        rubric: RubricInput,
                        subjectQuery: String,
                        config: LlmRequestConfig,
                        outputLanguage: LanguageTag,
                    ) = current.analyze(rubric, subjectQuery, config, outputLanguage)
                }
            val vm = ScoreAnalysisViewModel(service = delegatingService)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key", VI)
            vm.reset()
            current = secondService
            secondService.complete(Result.success(sampleReport()))
            vm.analyze("Title2", "Rubric text2", "Beta", "sk-valid-key-2", VI)

            assertIs<AnalysisUiState.Success>(vm.uiState.value)
            assertEquals(1, firstService.calls)
            assertEquals(1, secondService.calls)
        }

    @Test
    fun analyze_afterErrorStartsNewRequest() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)
            vm.analyze("", "Rubric text", "Acme", "sk-valid-key", VI)
            assertIs<AnalysisUiState.Error>(vm.uiState.value)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key", VI)

            assertEquals(AnalysisUiState.Loading, vm.uiState.value)
            assertEquals(1, service.calls)
        }
}
