package com.tekome.vcman.presentation

import com.tekome.vcman.data.AnalysisError
import com.tekome.vcman.data.AnalysisException
import com.tekome.vcman.data.LlmProvider
import com.tekome.vcman.data.LlmRequestConfig
import com.tekome.vcman.data.ScoreAnalysisService
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

        override suspend fun analyze(
            rubric: RubricInput,
            subjectQuery: String,
            config: LlmRequestConfig,
        ): Result<ProjectScoreReport> {
            calls++
            lastRubric = rubric
            lastSubjectQuery = subjectQuery
            lastConfig = config
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

                vm.analyze(blank, "Rubric text", "Acme", "sk-valid-key")

                val state = assertIs<AnalysisUiState.Error>(vm.uiState.value)
                assertEquals(0, service.calls)
                assertFalse("sk-valid-key" in state.message)
            }
        }

    @Test
    fun analyze_allFieldsBlankListsEveryMissingLabel() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)

            vm.analyze("", "  ", "", "")

            val state = assertIs<AnalysisUiState.Error>(vm.uiState.value)
            assertEquals("Please fill in: Rubric title, Rubric text, Subject, API key.", state.message)
            assertEquals(0, service.calls)
        }

    @Test
    fun analyze_validInputEmitsLoadingSynchronously() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key")

            assertEquals(AnalysisUiState.Loading, vm.uiState.value)
            assertEquals(1, service.calls)
        }

    @Test
    fun analyze_validInputBuildsAnthropicConfigWithoutSearchTool() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key")

            assertEquals(RubricInput(title = "Title", text = "Rubric text"), service.lastRubric)
            assertEquals("Acme", service.lastSubjectQuery)
            val config = service.lastConfig
            assertEquals(LlmProvider.Anthropic, config?.provider)
            assertNull(config?.searchTool)
            assertEquals("sk-valid-key", config?.apiKey?.value)
        }

    @Test
    fun analyze_successResultEmitsSuccessWithSameReport() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)
            val report = sampleReport()

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key")
            service.complete(Result.success(report))

            val state = assertIs<AnalysisUiState.Success>(vm.uiState.value)
            assertSame(report, state.report)
        }

    @Test
    fun analyze_analysisExceptionFailureEmitsMappedError() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key")
            service.complete(Result.failure(AnalysisException(AnalysisError.Network)))

            val state = assertIs<AnalysisUiState.Error>(vm.uiState.value)
            assertEquals(userMessageFor(AnalysisError.Network), state.message)
        }

    @Test
    fun analyze_unknownFailureEmitsFallbackError() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key")
            service.complete(Result.failure(IllegalStateException("apiKey=sk-123 boom")))

            val state = assertIs<AnalysisUiState.Error>(vm.uiState.value)
            assertEquals(UNEXPECTED_ERROR_MESSAGE, state.message)
        }

    @Test
    fun analyze_whileLoadingIsNoOp() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key")
            vm.analyze("Other title", "Other text", "Other", "other-key")

            assertEquals(AnalysisUiState.Loading, vm.uiState.value)
            assertEquals(1, service.calls)
        }

    @Test
    fun reset_duringLoadingCancelsJobSoLateResultCannotOverwriteIdle() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key")
            vm.reset()
            service.complete(Result.success(sampleReport()))

            assertEquals(AnalysisUiState.Idle, vm.uiState.value)
        }

    @Test
    fun reset_fromSuccessReturnsIdle() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)
            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key")
            service.complete(Result.success(sampleReport()))
            assertIs<AnalysisUiState.Success>(vm.uiState.value)

            vm.reset()

            assertEquals(AnalysisUiState.Idle, vm.uiState.value)
        }

    @Test
    fun reset_fromErrorReturnsIdle() =
        runTest {
            val vm = ScoreAnalysisViewModel(service = FakeScoreAnalysisService())
            vm.analyze("", "Rubric text", "Acme", "sk-valid-key")
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
                    ) = current.analyze(rubric, subjectQuery, config)
                }
            val vm = ScoreAnalysisViewModel(service = delegatingService)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key")
            vm.reset()
            current = secondService
            secondService.complete(Result.success(sampleReport()))
            vm.analyze("Title2", "Rubric text2", "Beta", "sk-valid-key-2")

            assertIs<AnalysisUiState.Success>(vm.uiState.value)
            assertEquals(1, firstService.calls)
            assertEquals(1, secondService.calls)
        }

    @Test
    fun analyze_afterErrorStartsNewRequest() =
        runTest {
            val service = FakeScoreAnalysisService()
            val vm = ScoreAnalysisViewModel(service = service)
            vm.analyze("", "Rubric text", "Acme", "sk-valid-key")
            assertIs<AnalysisUiState.Error>(vm.uiState.value)

            vm.analyze("Title", "Rubric text", "Acme", "sk-valid-key")

            assertEquals(AnalysisUiState.Loading, vm.uiState.value)
            assertEquals(1, service.calls)
        }
}
