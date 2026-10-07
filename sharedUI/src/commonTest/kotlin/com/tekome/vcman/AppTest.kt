package com.tekome.vcman

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import com.tekome.vcman.data.LlmProviderType
import com.tekome.vcman.data.LlmRequestConfig
import com.tekome.vcman.data.ScoreAnalysisService
import com.tekome.vcman.domain.LanguageTag
import com.tekome.vcman.domain.ProjectScoreReport
import com.tekome.vcman.domain.RubricInput
import com.tekome.vcman.presentation.AnalysisFailure
import com.tekome.vcman.presentation.AnalysisUiState
import com.tekome.vcman.presentation.ScoreAnalysisViewModel
import com.tekome.vcman.ui.ComposeUiTestRunner
import com.tekome.vcman.ui.ScoreReportScreenTags
import com.tekome.vcman.ui.SettingsScreenTags
import com.tekome.vcman.ui.SetupScreenTags
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals

private val fixtureReport =
    ProjectScoreReport(
        subjectName = "S",
        rubricTitle = "R",
        overallSummary = "",
        sections = emptyList(),
        generatedAtEpochMillis = 0L,
    )

class AnalysisUiStateProjectionTest {
    private data class Expected(
        val loading: Boolean,
        val error: AnalysisFailure?,
    )

    private val cases: List<Pair<AnalysisUiState, Expected>> =
        listOf(
            AnalysisUiState.Idle to Expected(loading = false, error = null),
            AnalysisUiState.Loading to Expected(loading = true, error = null),
            AnalysisUiState.Success(fixtureReport) to Expected(loading = false, error = null),
            AnalysisUiState.Error(AnalysisFailure.Network) to Expected(loading = false, error = AnalysisFailure.Network),
        )

    @Test
    fun projection_matchesExpectedForEveryState() {
        cases.forEach { (state, expected) ->
            assertEquals(expected.loading, state.isLoading, "isLoading for $state")
            assertEquals(expected.error, state.errorFailureOrNull, "errorFailureOrNull for $state")
        }
    }
}

@OptIn(ExperimentalTestApi::class)
class AppContentTest : ComposeUiTestRunner() {
    @Test
    fun analyze_forwardsCurrentValues_whenIdle() =
        runComposeUiTest {
            val calls = mutableListOf<List<String>>()
            setContent {
                AppContent(
                    uiState = AnalysisUiState.Idle,
                    onAnalyze = { title, text, subject -> calls += listOf(title, text, subject) },
                    onAnalyzeAgain = {},
                    settingsRepository = InMemorySettingsRepository(),
                )
            }

            onNodeWithTag(SetupScreenTags.RUBRIC_TITLE).performTextInput("T")
            onNodeWithTag(SetupScreenTags.SUBJECT).performTextInput("Bitcoin")
            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            assertEquals(1, calls.size)
            assertEquals(listOf("T", "", "Bitcoin"), calls.single())
            onNodeWithTag(SetupScreenTags.LOADING).assertDoesNotExist()
            onNodeWithTag(SetupScreenTags.ERROR).assertDoesNotExist()
        }

    @Test
    fun loading_disablesAnalyze_andShowsIndicator() =
        runComposeUiTest {
            setContent {
                AppContent(
                    uiState = AnalysisUiState.Loading,
                    onAnalyze = { _, _, _ -> },
                    onAnalyzeAgain = {},
                    settingsRepository = InMemorySettingsRepository(),
                )
            }

            onNodeWithTag(SetupScreenTags.LOADING).assertExists()
            onNodeWithTag(SetupScreenTags.ANALYZE).assertIsNotEnabled()
        }

    @Test
    fun error_showsMessage_andKeepsAnalyzeEnabled() =
        runComposeUiTest {
            setContent {
                AppContent(
                    uiState = AnalysisUiState.Error(AnalysisFailure.AmbiguousSubject("Rate limited")),
                    onAnalyze = { _, _, _ -> },
                    onAnalyzeAgain = {},
                    settingsRepository = InMemorySettingsRepository(),
                )
            }

            onNodeWithTag(SetupScreenTags.ERROR).assertTextContains("Rate limited", substring = true)
            onNodeWithTag(SetupScreenTags.LOADING).assertDoesNotExist()
            onNodeWithTag(SetupScreenTags.ANALYZE).assertIsEnabled()
        }

    @Test
    fun success_showsScoreReport_notSetup() =
        runComposeUiTest {
            setContent {
                AppContent(
                    uiState = AnalysisUiState.Success(fixtureReport),
                    onAnalyze = { _, _, _ -> },
                    onAnalyzeAgain = {},
                    settingsRepository = InMemorySettingsRepository(),
                )
            }

            onNodeWithTag(ScoreReportScreenTags.HEADER).assertExists()
            onNodeWithTag(SetupScreenTags.ANALYZE).assertDoesNotExist()
        }

    @Test
    fun success_analyzeAgain_forwardsCallbackOnce() =
        runComposeUiTest {
            var calls = 0
            setContent {
                AppContent(
                    uiState = AnalysisUiState.Success(fixtureReport),
                    onAnalyze = { _, _, _ -> },
                    onAnalyzeAgain = { calls++ },
                    settingsRepository = InMemorySettingsRepository(),
                )
            }

            onNodeWithTag(ScoreReportScreenTags.ANALYZE_AGAIN).performScrollTo().performClick()

            assertEquals(1, calls)
        }

    @Test
    fun roundTrip_preservesAllInputs() =
        runComposeUiTest {
            var state by mutableStateOf<AnalysisUiState>(AnalysisUiState.Idle)
            val analyzeCalls = mutableListOf<List<String>>()
            setContent {
                AppContent(
                    uiState = state,
                    onAnalyze = { title, text, subject ->
                        analyzeCalls += listOf(title, text, subject)
                    },
                    onAnalyzeAgain = { state = AnalysisUiState.Idle },
                    settingsRepository = InMemorySettingsRepository(),
                )
            }

            onNodeWithTag(SetupScreenTags.RUBRIC_TITLE).performTextInput("T")
            onNodeWithTag(SetupScreenTags.RUBRIC_TEXT).performTextInput("Body")
            onNodeWithTag(SetupScreenTags.SUBJECT).performTextInput("Bitcoin")

            state = AnalysisUiState.Loading
            waitForIdle()
            state = AnalysisUiState.Success(fixtureReport)
            waitForIdle()

            // SetupScreen must have actually left composition (not merely be hidden), otherwise this
            // test would pass even if input state were wrongly reset.
            onNodeWithTag(ScoreReportScreenTags.HEADER).assertExists()
            onNodeWithTag(SetupScreenTags.SUBJECT).assertDoesNotExist()

            onNodeWithTag(ScoreReportScreenTags.ANALYZE_AGAIN).performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag(SetupScreenTags.RUBRIC_TITLE)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("T")))
            onNodeWithTag(SetupScreenTags.SUBJECT)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Bitcoin")))

            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            assertEquals(listOf("T", "Body", "Bitcoin"), analyzeCalls.single())
        }
}

@OptIn(ExperimentalTestApi::class)
class AppWiringTest : ComposeUiTestRunner() {
    private class NeverCalledService : ScoreAnalysisService {
        override suspend fun analyze(
            rubric: RubricInput,
            subjectQuery: String,
            config: LlmRequestConfig,
            outputLanguage: LanguageTag,
        ): Result<ProjectScoreReport> {
            error("should not be called when validation fails")
        }
    }

    private class DeferredService(
        private val deferred: CompletableDeferred<Result<ProjectScoreReport>>,
    ) : ScoreAnalysisService {
        override suspend fun analyze(
            rubric: RubricInput,
            subjectQuery: String,
            config: LlmRequestConfig,
            outputLanguage: LanguageTag,
        ): Result<ProjectScoreReport> = deferred.await()
    }

    /** Records every call's inputs and resolves them, in order, from [results]. */
    private class RecordingService(
        private val results: List<CompletableDeferred<Result<ProjectScoreReport>>>,
    ) : ScoreAnalysisService {
        val calls = mutableListOf<List<String>>()
        val configs = mutableListOf<LlmRequestConfig>()
        private var callIndex = 0

        override suspend fun analyze(
            rubric: RubricInput,
            subjectQuery: String,
            config: LlmRequestConfig,
            outputLanguage: LanguageTag,
        ): Result<ProjectScoreReport> {
            calls += listOf(rubric.title, rubric.text, subjectQuery)
            configs += config
            return results[callIndex++].await()
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.fillRubric() {
        onNodeWithTag(SetupScreenTags.RUBRIC_TITLE).performTextInput("T")
        onNodeWithTag(SetupScreenTags.RUBRIC_TEXT).performTextInput("Body")
        onNodeWithTag(SetupScreenTags.SUBJECT).performTextInput("Bitcoin")
    }

    @Test
    fun blankFields_showsValidationError_withoutCallingService() =
        runComposeUiTest {
            val repository = InMemorySettingsRepository(configuredSettings())
            setContent {
                App(repository, viewModel = ScoreAnalysisViewModel(repository, NeverCalledService()))
            }

            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag(SetupScreenTags.ERROR).assertExists()
        }

    @Test
    fun notConfigured_showsErrorAndSettingsEntryPoint_withoutCallingService() =
        runComposeUiTest {
            val repository = InMemorySettingsRepository(settings = null)
            setContent {
                App(repository, viewModel = ScoreAnalysisViewModel(repository, NeverCalledService()))
            }

            fillRubric()
            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag(SetupScreenTags.ERROR).assertExists()
            onNodeWithTag(SetupScreenTags.OPEN_SETTINGS).assertExists()
        }

    @Test
    fun validFields_showLoading_thenError_onServiceFailure() =
        runComposeUiTest {
            val deferred = CompletableDeferred<Result<ProjectScoreReport>>()
            val repository = InMemorySettingsRepository(configuredSettings())
            setContent {
                App(repository, viewModel = ScoreAnalysisViewModel(repository, DeferredService(deferred)))
            }

            fillRubric()
            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag(SetupScreenTags.LOADING).assertExists()
            onNodeWithTag(SetupScreenTags.ANALYZE).assertIsNotEnabled()

            deferred.complete(Result.failure(Exception("boom")))
            waitForIdle()

            onNodeWithTag(SetupScreenTags.LOADING).assertDoesNotExist()
            onNodeWithTag(SetupScreenTags.ERROR).assertExists()
        }

    @Test
    fun success_thenAnalyzeAgain_keepsInputs_andReanalyzes() =
        runComposeUiTest {
            val firstResult = CompletableDeferred<Result<ProjectScoreReport>>()
            val secondResult = CompletableDeferred<Result<ProjectScoreReport>>()
            val service = RecordingService(listOf(firstResult, secondResult))
            val repository = InMemorySettingsRepository(configuredSettings())
            setContent {
                App(repository, viewModel = ScoreAnalysisViewModel(repository, service))
            }

            fillRubric()
            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag(SetupScreenTags.LOADING).assertExists()
            firstResult.complete(Result.success(fixtureReport))
            waitForIdle()

            onNodeWithTag(ScoreReportScreenTags.HEADER).assertExists()

            onNodeWithTag(ScoreReportScreenTags.ANALYZE_AGAIN).performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag(SetupScreenTags.ANALYZE).assertIsEnabled()
            onNodeWithTag(SetupScreenTags.ERROR).assertDoesNotExist()
            onNodeWithTag(SetupScreenTags.LOADING).assertDoesNotExist()

            // No re-typing: the previously entered values must still be there.
            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag(SetupScreenTags.LOADING).assertExists()
            secondResult.complete(Result.success(fixtureReport))
            waitForIdle()

            assertEquals(2, service.calls.size)
            assertEquals(service.calls[0], service.calls[1])
            assertEquals(listOf("T", "Body", "Bitcoin"), service.calls[0])
        }

    @Test
    fun validFields_withoutSavedSettings_showError_withoutCallingService() =
        runComposeUiTest {
            val repository = InMemorySettingsRepository()
            val service = RecordingService(emptyList())
            setContent {
                App(repository, viewModel = ScoreAnalysisViewModel(repository, service))
            }

            fillRubric()
            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag(SetupScreenTags.LOADING).assertDoesNotExist()
            onNodeWithTag(SetupScreenTags.ERROR).assertExists()
            assertEquals(emptyList(), service.calls)
        }

    @Test
    fun notConfiguredError_isClearedAfterVisitingSettings() =
        runComposeUiTest {
            val repository = InMemorySettingsRepository()
            setContent {
                App(repository, viewModel = ScoreAnalysisViewModel(repository, RecordingService(emptyList())))
            }
            fillRubric()
            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(SetupScreenTags.ERROR).assertExists()

            onNodeWithTag(SetupScreenTags.OPEN_SETTINGS).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(SettingsScreenTags.BACK).performClick()
            waitForIdle()

            onNodeWithTag(SetupScreenTags.ERROR).assertDoesNotExist()
        }

    @Test
    fun settings_openAndBack_keepsRubricAndSubject() =
        runComposeUiTest {
            val repository = InMemorySettingsRepository()
            setContent {
                App(repository, viewModel = ScoreAnalysisViewModel(repository, NeverCalledService()))
            }
            fillRubric()

            onNodeWithTag(SetupScreenTags.OPEN_SETTINGS).performClick()
            waitForIdle()
            onNodeWithTag(SettingsScreenTags.TITLE).assertExists()
            onNodeWithTag(SetupScreenTags.SUBJECT).assertDoesNotExist()

            onNodeWithTag(SettingsScreenTags.BACK).performClick()
            waitForIdle()

            onNodeWithTag(SetupScreenTags.RUBRIC_TITLE)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("T")))
            onNodeWithTag(SetupScreenTags.SUBJECT)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Bitcoin")))
        }

    @Test
    fun savingInSettings_thenAnalyze_usesSavedSettings() =
        runComposeUiTest {
            val result = CompletableDeferred<Result<ProjectScoreReport>>()
            val service = RecordingService(listOf(result))
            val repository = InMemorySettingsRepository()
            setContent {
                App(repository, viewModel = ScoreAnalysisViewModel(repository, service))
            }
            fillRubric()

            onNodeWithTag(SetupScreenTags.OPEN_SETTINGS).performClick()
            waitForIdle()
            onNodeWithTag(SettingsScreenTags.provider(LlmProviderType.AnthropicCompatible)).performClick()
            onNodeWithTag(SettingsScreenTags.API_KEY).performTextInput("sk-from-settings")
            onNodeWithTag(SettingsScreenTags.SAVE).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(SettingsScreenTags.SAVE_STATUS).assertExists()
            onNodeWithTag(SettingsScreenTags.BACK).performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            val settings = service.configs.single().settings
            assertEquals(LlmProviderType.AnthropicCompatible, settings.providerType)
            assertEquals("sk-from-settings", settings.apiKey.value)
            assertEquals(LlmProviderType.AnthropicCompatible.defaultBaseUrl, settings.baseUrl)
            assertEquals(settings, repository.settings)
            result.complete(Result.success(fixtureReport))
        }
}
