package com.tekome.vcman.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tekome.vcman.data.ApiKey
import com.tekome.vcman.data.KoogScoreAnalysisService
import com.tekome.vcman.data.LlmProvider
import com.tekome.vcman.data.LlmRequestConfig
import com.tekome.vcman.data.ScoreAnalysisService
import com.tekome.vcman.domain.RubricInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State machine for the score-analysis screen: pure state + orchestration, no UI code.
 *
 * - `analyze(...)` is ignored (no-op) while [uiState] is already [AnalysisUiState.Loading].
 * - `reset()` cancels any in-flight [analyze] job before returning to [AnalysisUiState.Idle], so a
 *   late-arriving result can never overwrite `Idle` with `Success`/`Error`.
 */
class ScoreAnalysisViewModel(
    private val service: ScoreAnalysisService = KoogScoreAnalysisService(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private var analyzeJob: Job? = null

    fun analyze(
        rubricTitle: String,
        rubricText: String,
        subjectQuery: String,
        apiKey: String,
    ) {
        if (_uiState.value is AnalysisUiState.Loading) return

        val requiredFields =
            listOf(
                RequiredField("Rubric title", rubricTitle),
                RequiredField("Rubric text", rubricText),
                RequiredField("Subject", subjectQuery),
                RequiredField("API key", apiKey),
            )
        blankFieldsMessage(requiredFields)?.let {
            _uiState.value = AnalysisUiState.Error(it)
            return
        }

        _uiState.value = AnalysisUiState.Loading

        val rubric = RubricInput(title = rubricTitle, text = rubricText)
        val config =
            LlmRequestConfig(
                provider = LlmProvider.Anthropic,
                apiKey = ApiKey(apiKey),
                searchTool = null,
            )

        analyzeJob =
            viewModelScope.launch {
                // No try/catch here: ScoreAnalysisService.analyze always rethrows
                // CancellationException instead of wrapping it in Result.failure (see
                // data.runAnalysis), so a cancelled job simply stops before reaching the
                // assignment below - it never overwrites a state set by reset().
                val result = service.analyze(rubric, subjectQuery, config)
                _uiState.value =
                    result.fold(
                        onSuccess = { AnalysisUiState.Success(it) },
                        onFailure = { AnalysisUiState.Error(userMessageFor(it)) },
                    )
            }
    }

    fun reset() {
        analyzeJob?.cancel()
        analyzeJob = null
        _uiState.value = AnalysisUiState.Idle
    }
}
