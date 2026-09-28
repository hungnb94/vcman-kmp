package com.tekome.vcman

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tekome.vcman.data.LlmRequestConfig
import com.tekome.vcman.data.ScoreAnalysisService
import com.tekome.vcman.domain.ProjectScoreReport
import com.tekome.vcman.domain.RubricInput
import com.tekome.vcman.presentation.AnalysisUiState
import com.tekome.vcman.presentation.ScoreAnalysisViewModel
import com.tekome.vcman.ui.ComposeUiTestRunner
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
        val error: String?,
    )

    private val cases: List<Pair<AnalysisUiState, Expected>> =
        listOf(
            AnalysisUiState.Idle to Expected(loading = false, error = null),
            AnalysisUiState.Loading to Expected(loading = true, error = null),
            AnalysisUiState.Success(fixtureReport) to Expected(loading = false, error = null),
            AnalysisUiState.Error("Rate limited") to Expected(loading = false, error = "Rate limited"),
        )

    @Test
    fun projection_matchesExpectedForEveryState() {
        cases.forEach { (state, expected) ->
            assertEquals(expected.loading, state.isLoading, "isLoading for $state")
            assertEquals(expected.error, state.errorMessageOrNull, "errorMessageOrNull for $state")
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
                    onAnalyze = { title, text, subject, apiKey -> calls += listOf(title, text, subject, apiKey) },
                )
            }

            onNodeWithTag(SetupScreenTags.RUBRIC_TITLE).performTextInput("T")
            onNodeWithTag(SetupScreenTags.SUBJECT).performTextInput("Bitcoin")
            onNodeWithTag(SetupScreenTags.API_KEY).performTextInput("sk-123")
            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            assertEquals(1, calls.size)
            assertEquals(listOf("T", "", "Bitcoin", "sk-123"), calls.single())
            onNodeWithTag(SetupScreenTags.LOADING).assertDoesNotExist()
            onNodeWithTag(SetupScreenTags.ERROR).assertDoesNotExist()
        }

    @Test
    fun loading_disablesAnalyze_andShowsIndicator() =
        runComposeUiTest {
            setContent {
                AppContent(uiState = AnalysisUiState.Loading, onAnalyze = { _, _, _, _ -> })
            }

            onNodeWithTag(SetupScreenTags.LOADING).assertExists()
            onNodeWithTag(SetupScreenTags.ANALYZE).assertIsNotEnabled()
        }

    @Test
    fun error_showsMessage_andKeepsAnalyzeEnabled() =
        runComposeUiTest {
            setContent {
                AppContent(uiState = AnalysisUiState.Error("Rate limited"), onAnalyze = { _, _, _, _ -> })
            }

            onNodeWithTag(SetupScreenTags.ERROR).assertTextEquals("Rate limited")
            onNodeWithTag(SetupScreenTags.LOADING).assertDoesNotExist()
            onNodeWithTag(SetupScreenTags.ANALYZE).assertIsEnabled()
        }

    @Test
    fun success_showsNeitherLoadingNorError() =
        runComposeUiTest {
            setContent {
                AppContent(uiState = AnalysisUiState.Success(fixtureReport), onAnalyze = { _, _, _, _ -> })
            }

            onNodeWithTag(SetupScreenTags.LOADING).assertDoesNotExist()
            onNodeWithTag(SetupScreenTags.ERROR).assertDoesNotExist()
            onNodeWithTag(SetupScreenTags.ANALYZE).assertIsEnabled()
        }
}

@OptIn(ExperimentalTestApi::class)
class AppWiringTest : ComposeUiTestRunner() {
    private class NeverCalledService : ScoreAnalysisService {
        override suspend fun analyze(
            rubric: RubricInput,
            subjectQuery: String,
            config: LlmRequestConfig,
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
        ): Result<ProjectScoreReport> = deferred.await()
    }

    @Test
    fun blankFields_showsValidationError_withoutCallingService() =
        runComposeUiTest {
            setContent {
                App(viewModel = ScoreAnalysisViewModel(service = NeverCalledService()))
            }

            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag(SetupScreenTags.ERROR).assertExists()
        }

    @Test
    fun validFields_showLoading_thenError_onServiceFailure() =
        runComposeUiTest {
            val deferred = CompletableDeferred<Result<ProjectScoreReport>>()
            setContent {
                App(viewModel = ScoreAnalysisViewModel(service = DeferredService(deferred)))
            }

            onNodeWithTag(SetupScreenTags.RUBRIC_TITLE).performTextInput("T")
            onNodeWithTag(SetupScreenTags.RUBRIC_TEXT).performTextInput("Body")
            onNodeWithTag(SetupScreenTags.SUBJECT).performTextInput("Bitcoin")
            onNodeWithTag(SetupScreenTags.API_KEY).performTextInput("sk-123")
            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag(SetupScreenTags.LOADING).assertExists()
            onNodeWithTag(SetupScreenTags.ANALYZE).assertIsNotEnabled()

            deferred.complete(Result.failure(Exception("boom")))
            waitForIdle()

            onNodeWithTag(SetupScreenTags.LOADING).assertDoesNotExist()
            onNodeWithTag(SetupScreenTags.ERROR).assertExists()
        }
}
