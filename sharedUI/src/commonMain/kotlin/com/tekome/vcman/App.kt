package com.tekome.vcman

import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tekome.vcman.data.ConnectionTester
import com.tekome.vcman.data.LlmConnectionTester
import com.tekome.vcman.data.SettingsRepository
import com.tekome.vcman.presentation.AnalysisFailure
import com.tekome.vcman.presentation.AnalysisUiState
import com.tekome.vcman.presentation.ScoreAnalysisViewModel
import com.tekome.vcman.presentation.SettingsViewModel
import com.tekome.vcman.ui.SettingsScreen
import com.tekome.vcman.ui.ScoreReportScreen
import com.tekome.vcman.ui.SetupInput
import com.tekome.vcman.ui.SetupScreen
import com.tekome.vcman.ui.asText
import com.tekome.vcman.ui.rememberContentLanguage
import kotlinx.serialization.Serializable

@Serializable
internal data object HomeRoute

@Serializable
internal data object SettingsRoute

@Composable
fun App(
    settingsRepository: SettingsRepository,
    viewModel: ScoreAnalysisViewModel = viewModel { ScoreAnalysisViewModel(settingsRepository) },
) {
    MaterialTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val language = rememberContentLanguage()
        AppContent(
            uiState = uiState,
            onAnalyze = { title, text, subject -> viewModel.analyze(title, text, subject, language) },
            onAnalyzeAgain = viewModel::reset,
            settingsRepository = settingsRepository,
            modifier = Modifier.safeContentPadding(),
        )
    }
}

@Composable
internal fun AppContent(
    uiState: AnalysisUiState,
    onAnalyze: (rubricTitle: String, rubricText: String, subject: String) -> Unit,
    onAnalyzeAgain: () -> Unit,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
    connectionTester: ConnectionTester = remember { LlmConnectionTester() },
) {
    val navController = rememberNavController()
    // Held above the NavHost so the rubric/subject survive a trip to Settings and back.
    var setupInput by rememberSaveable(stateSaver = setupInputSaver) { mutableStateOf(SetupInput()) }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
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
                        onOpenSettings = { navController.navigate(SettingsRoute) },
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
        composable<SettingsRoute> {
            val settingsViewModel = viewModel { SettingsViewModel(settingsRepository, connectionTester) }
            val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                modifier = modifier,
                state = state,
                onSelectProvider = settingsViewModel::selectProvider,
                onApiKeyChange = settingsViewModel::updateApiKey,
                onBaseUrlChange = settingsViewModel::updateBaseUrl,
                onModelChange = settingsViewModel::updateModel,
                onSave = settingsViewModel::save,
                onTestConnection = settingsViewModel::testConnection,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private val setupInputSaver =
    listSaver(
        save = { listOf(it.rubricTitle, it.rubricText, it.subject) },
        restore = { SetupInput(rubricTitle = it[0], rubricText = it[1], subject = it[2]) },
    )

internal val AnalysisUiState.isLoading: Boolean
    get() = this is AnalysisUiState.Loading

internal val AnalysisUiState.errorFailureOrNull: AnalysisFailure?
    get() = (this as? AnalysisUiState.Error)?.failure
