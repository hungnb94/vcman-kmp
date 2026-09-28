package com.tekome.vcman

import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekome.vcman.presentation.AnalysisUiState
import com.tekome.vcman.presentation.ScoreAnalysisViewModel
import com.tekome.vcman.ui.SetupScreen

/**
 * State owner of the app: creates/retrieves [ScoreAnalysisViewModel] through the composition's
 * `ViewModelStore` and collects [ScoreAnalysisViewModel.uiState] in a lifecycle-aware way.
 *
 * This is an interim entry point (issue #32): it only renders [SetupScreen]. Once
 * `ScoreReportScreen` (#11) lands, #12 will add screen switching in [AppContent] without needing
 * to touch how the ViewModel is created or how state is collected here.
 */
@Composable
fun App(viewModel: ScoreAnalysisViewModel = viewModel { ScoreAnalysisViewModel() }) {
    MaterialTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        AppContent(
            uiState = uiState,
            onAnalyze = viewModel::analyze,
            modifier = Modifier.safeContentPadding(),
        )
    }
}

/**
 * Stateless content renderer: picks which screen to show for the current [uiState]. Does not know
 * that a ViewModel exists. Adding a new screen for a given state (e.g. `is Success ->
 * ScoreReportScreen(...)` for #12) only requires a new branch here.
 */
@Composable
internal fun AppContent(
    uiState: AnalysisUiState,
    onAnalyze: (rubricTitle: String, rubricText: String, subject: String, apiKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        // #12 will split this out: is AnalysisUiState.Success -> ScoreReportScreen(report = uiState.report, ...)
        AnalysisUiState.Idle,
        AnalysisUiState.Loading,
        is AnalysisUiState.Error,
        is AnalysisUiState.Success,
        -> {
            SetupScreen(
                modifier = modifier,
                loading = uiState.isLoading,
                error = uiState.errorMessageOrNull,
                onAnalyze = onAnalyze,
            )
        }
    }
}

/** True only while an analysis request is in flight. */
internal val AnalysisUiState.isLoading: Boolean
    get() = this is AnalysisUiState.Loading

/** The error message when [AnalysisUiState] is [AnalysisUiState.Error], `null` otherwise. */
internal val AnalysisUiState.errorMessageOrNull: String?
    get() = (this as? AnalysisUiState.Error)?.message
