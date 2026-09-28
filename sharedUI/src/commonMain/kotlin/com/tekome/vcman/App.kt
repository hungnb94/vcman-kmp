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

@Composable
internal fun AppContent(
    uiState: AnalysisUiState,
    onAnalyze: (rubricTitle: String, rubricText: String, subject: String, apiKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
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

internal val AnalysisUiState.isLoading: Boolean
    get() = this is AnalysisUiState.Loading

internal val AnalysisUiState.errorMessageOrNull: String?
    get() = (this as? AnalysisUiState.Error)?.message
