package com.tekome.vcman.presentation

import com.tekome.vcman.domain.ProjectScoreReport

/**
 * Closed set of UI states for the score-analysis screen. Exactly four branches: adding a new
 * one requires updating every consumer's exhaustive `when` (compiler-enforced).
 */
sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState

    data object Loading : AnalysisUiState

    data class Success(
        val report: ProjectScoreReport,
    ) : AnalysisUiState

    data class Error(
        val message: String,
    ) : AnalysisUiState
}
