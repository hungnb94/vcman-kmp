package com.tekome.vcman

import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekome.vcman.presentation.AnalysisFailure
import com.tekome.vcman.presentation.AnalysisUiState
import com.tekome.vcman.presentation.ScoreAnalysisViewModel
import com.tekome.vcman.ui.ScoreReportScreen
import com.tekome.vcman.ui.SetupInput
import com.tekome.vcman.ui.SetupScreen
import com.tekome.vcman.ui.asText
import com.tekome.vcman.ui.rememberContentLanguage

@Composable
fun App(viewModel: ScoreAnalysisViewModel = viewModel { ScoreAnalysisViewModel() }) {
    MaterialTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val language = rememberContentLanguage()
        AppContent(
            uiState = uiState,
            onAnalyze = { title, text, subject, apiKey -> viewModel.analyze(title, text, subject, apiKey, language) },
            onAnalyzeAgain = viewModel::reset,
            modifier = Modifier.safeContentPadding(),
        )
    }
}

@Composable
internal fun AppContent(
    uiState: AnalysisUiState,
    onAnalyze: (rubricTitle: String, rubricText: String, subject: String, apiKey: String) -> Unit,
    onAnalyzeAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hoisted here (not inside SetupScreen) so it survives the `Success` branch below (AC #11):
    // AppContent stays in composition across every `when` branch, SetupScreen does not.
    // `remember` only, never `rememberSaveable` - apiKey must not enter the saved-instance state.
    var setupInput by remember { mutableStateOf(SetupInput()) }

    when (uiState) {
        AnalysisUiState.Idle,
        AnalysisUiState.Loading,
        is AnalysisUiState.Error,
        -> {
            SetupScreen(
                modifier = modifier,
                input = setupInput,
                onInputChange = { setupInput = it },
                loading = uiState.isLoading,
                error = uiState.errorFailureOrNull?.asText(),
                onAnalyze = onAnalyze,
            )
        }

        is AnalysisUiState.Success -> {
            ScoreReportScreen(
                modifier = modifier,
                report = uiState.report,
                onAnalyzeAgain = onAnalyzeAgain,
            )
        }
    }
}

internal val AnalysisUiState.isLoading: Boolean
    get() = this is AnalysisUiState.Loading

internal val AnalysisUiState.errorFailureOrNull: AnalysisFailure?
    get() = (this as? AnalysisUiState.Error)?.failure
