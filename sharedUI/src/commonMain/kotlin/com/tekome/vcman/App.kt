package com.tekome.vcman

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tekome.vcman.presentation.AnalysisFailure
import com.tekome.vcman.presentation.AnalysisUiState
import com.tekome.vcman.presentation.ScoreAnalysisViewModel
import com.tekome.vcman.presentation.SettingsViewModel
import com.tekome.vcman.ui.ScoreReportScreen
import com.tekome.vcman.ui.SettingsScreen
import com.tekome.vcman.ui.SetupInput
import com.tekome.vcman.ui.SetupScreen
import com.tekome.vcman.ui.asText
import com.tekome.vcman.ui.rememberContentLanguage
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.serialization.Serializable

@Serializable
internal data object HomeRoute

@Serializable
internal data object SettingsRoute

@Composable
fun AppRoot(viewModelFactory: MetroViewModelFactory) {
    CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
        App()
    }
}

@Composable
fun App(viewModel: ScoreAnalysisViewModel = metroViewModel()) {
    MaterialTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val language = rememberContentLanguage()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppContent(
                uiState = uiState,
                onAnalyze = { title, text, subject ->
                    viewModel.analyze(
                        title,
                        text,
                        subject,
                        language,
                    )
                },
                onAnalyzeAgain = viewModel::reset,
                onOpenSettings = viewModel::clearError,
            )
        }
    }
}

@Composable
internal fun AppContent(
    uiState: AnalysisUiState,
    onAnalyze: (rubricTitle: String, rubricText: String, subject: String) -> Unit,
    onAnalyzeAgain: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    val navController = rememberNavController()
    // Held above the NavHost so the rubric/subject survive a trip to Settings and back.
    var setupInput by rememberSaveable(stateSaver = setupInputSaver) { mutableStateOf(SetupInput()) }

    val homeModifier = modifier.safeContentPadding()

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            when (uiState) {
                AnalysisUiState.Idle,
                AnalysisUiState.Loading,
                is AnalysisUiState.Error,
                -> {
                    SetupScreen(
                        modifier = homeModifier,
                        input = setupInput,
                        onInputChange = { setupInput = it },
                        loading = uiState.isLoading,
                        error = uiState.errorFailureOrNull?.asText(),
                        onAnalyze = onAnalyze,
                        onOpenSettings = {
                            // The error (e.g. NotConfigured) is about to be fixed in Settings; don't show it stale on return.
                            onOpenSettings()
                            navController.navigate(SettingsRoute)
                        },
                    )
                }

                is AnalysisUiState.Success -> {
                    ScoreReportScreen(
                        modifier = homeModifier,
                        report = uiState.report,
                        onAnalyzeAgain = onAnalyzeAgain,
                    )
                }
            }
        }
        composable<SettingsRoute> {
            val settingsViewModel: SettingsViewModel = metroViewModel()
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
