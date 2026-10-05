package com.tekome.vcman.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tekome.vcman.data.KoogScoreAnalysisService
import com.tekome.vcman.data.LlmRequestConfig
import com.tekome.vcman.data.ScoreAnalysisService
import com.tekome.vcman.data.SettingsRepository
import com.tekome.vcman.data.validate
import com.tekome.vcman.domain.LanguageTag
import com.tekome.vcman.domain.RubricInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScoreAnalysisViewModel(
    private val settingsRepository: SettingsRepository,
    private val service: ScoreAnalysisService = KoogScoreAnalysisService(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private var analyzeJob: Job? = null

    private var requestId: Long = 0L

    fun analyze(
        rubricTitle: String,
        rubricText: String,
        subjectQuery: String,
        outputLanguage: LanguageTag,
    ) {
        if (analyzeJob?.isActive == true) return

        val requiredFields =
            listOf(
                RequiredField(RequiredFieldId.RubricTitle, rubricTitle),
                RequiredField(RequiredFieldId.RubricText, rubricText),
                RequiredField(RequiredFieldId.Subject, subjectQuery),
            )
        missingFieldsFailure(requiredFields)?.let {
            _uiState.value = AnalysisUiState.Error(it)
            return
        }

        _uiState.value = AnalysisUiState.Loading

        val rubric = RubricInput(title = rubricTitle, text = rubricText)

        val thisRequestId = ++requestId
        analyzeJob =
            viewModelScope.launch {
                val result =
                    try {
                        val settings = settingsRepository.load()?.takeIf { it.validate().isEmpty() }
                        if (settings == null) {
                            Result.failure(NotConfiguredException)
                        } else {
                            service.analyze(rubric, subjectQuery, LlmRequestConfig(settings, searchTool = null), outputLanguage)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                if (thisRequestId == requestId) {
                    _uiState.value =
                        result.fold(
                            onSuccess = { AnalysisUiState.Success(it) },
                            onFailure = { AnalysisUiState.Error(it.toFailure()) },
                        )
                }
            }
    }

    fun reset() {
        requestId++
        analyzeJob?.cancel()
        analyzeJob = null
        _uiState.value = AnalysisUiState.Idle
    }
}
