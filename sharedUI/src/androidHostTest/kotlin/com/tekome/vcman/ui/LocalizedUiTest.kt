package com.tekome.vcman.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tekome.vcman.AppContent
import com.tekome.vcman.InMemorySettingsRepository
import com.tekome.vcman.data.ConnectionTestResult
import com.tekome.vcman.data.LlmProviderType
import com.tekome.vcman.data.SettingsFieldError
import com.tekome.vcman.presentation.ConnectionTestState
import com.tekome.vcman.presentation.SettingsUiState
import com.tekome.vcman.domain.LanguageTag
import com.tekome.vcman.domain.ProjectScoreReport
import com.tekome.vcman.domain.QuestionScoreResult
import com.tekome.vcman.domain.ScoreSectionResult
import com.tekome.vcman.presentation.AnalysisFailure
import com.tekome.vcman.presentation.AnalysisUiState
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locale is selected through Robolectric's `qualifiers`, which sets the Android resource
 * configuration that Compose Resources reads. Exact-text assertions live here (and not in
 * commonTest) because only this source set pins the locale; commonTest runs on devices/CI
 * whose locale is unknown.
 */
@OptIn(ExperimentalTestApi::class)
class LocalizedUiTest : ComposeUiTestRunner() {
    private val report =
        ProjectScoreReport(
            subjectName = "Acme",
            rubricTitle = "R",
            overallSummary = "",
            sections =
                listOf(
                    ScoreSectionResult(
                        name = "S",
                        questions =
                            listOf(
                                QuestionScoreResult(
                                    id = "Q1",
                                    label = "L",
                                    weight = 1.0,
                                    rawScore = 7.5,
                                    comment = "c",
                                    sourceUrl = null,
                                ),
                            ),
                    ),
                ),
            generatedAtEpochMillis = 0L,
        )

    /** Shows the setup screen with an API error; returns a reader for the language the app would ask the LLM for. */
    private fun androidx.compose.ui.test.ComposeUiTest.showSetupWithApiError(): () -> LanguageTag {
        var tag = LanguageTag.Default
        setContent {
            tag = rememberContentLanguage()
            AppContent(
                uiState = AnalysisUiState.Error(AnalysisFailure.Api(401)),
                onAnalyze = { _, _, _ -> },
                onAnalyzeAgain = {},
                settingsRepository = InMemorySettingsRepository(),
            )
        }
        waitForIdle()
        return { tag }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.scrollListTo(tag: String) {
        onNodeWithTag(ScoreReportScreenTags.LIST).performScrollToNode(hasTestTag(tag))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showReport() {
        setContent { ScoreReportScreen(report = report, onAnalyzeAgain = {}) }
    }

    @Test
    @Config(qualifiers = "vi")
    fun vietnamese_showsVietnameseText() =
        runComposeUiTest {
            val language = showSetupWithApiError()
            onNodeWithTag(SetupScreenTags.ANALYZE).assertTextEquals("Phân tích")
            onNodeWithTag(SetupScreenTags.ERROR).assertTextEquals("Dịch vụ AI đã từ chối yêu cầu (HTTP 401).")
            assertEquals("vi", language().value)
        }

    @Test
    @Config(qualifiers = "vi")
    fun vietnamese_reportUsesCommaAndVietnameseLabels() =
        runComposeUiTest {
            showReport()
            onNodeWithTag(ScoreReportScreenTags.HEADER_SCORE).assertTextEquals("7,5 / 10")
            scrollListTo(ScoreReportScreenTags.ANALYZE_AGAIN)
            onNodeWithTag(ScoreReportScreenTags.ANALYZE_AGAIN).assertTextEquals("Phân tích lại")
            val rawTag = ScoreReportScreenTags.questionField(0, 0, "raw")
            scrollListTo(rawTag)
            onNodeWithTag(rawTag).assertTextEquals("Điểm thô: 7,5 / 10")
        }

    @Test
    @Config(qualifiers = "en")
    fun english_showsEnglishText() =
        runComposeUiTest {
            val language = showSetupWithApiError()
            onNodeWithTag(SetupScreenTags.ANALYZE).assertTextEquals("Analyze")
            onNodeWithTag(SetupScreenTags.ERROR).assertTextEquals("The AI service rejected the request (HTTP 401).")
            assertEquals("en", language().value)
        }

    @Test
    @Config(qualifiers = "en")
    fun english_reportUsesDot() =
        runComposeUiTest {
            showReport()
            onNodeWithTag(ScoreReportScreenTags.HEADER_SCORE).assertTextEquals("7.5 / 10")
            scrollListTo(ScoreReportScreenTags.ANALYZE_AGAIN)
            onNodeWithTag(ScoreReportScreenTags.ANALYZE_AGAIN).assertTextEquals("Analyze again")
        }

    @Test
    @Config(qualifiers = "fr")
    fun unsupportedLocale_fallsBackToEnglishForUiAndContentLanguage() =
        runComposeUiTest {
            val language = showSetupWithApiError()
            onNodeWithTag(SetupScreenTags.ANALYZE).assertTextEquals("Analyze")
            onNodeWithTag(SetupScreenTags.ERROR).assertTextEquals("The AI service rejected the request (HTTP 401).")
            assertEquals("en", language().value)
        }

    private fun androidx.compose.ui.test.ComposeUiTest.showSettings(state: SettingsUiState) {
        setContent {
            SettingsScreen(
                state = state,
                onSelectProvider = {},
                onApiKeyChange = {},
                onBaseUrlChange = {},
                onModelChange = {},
                onSave = {},
                onTestConnection = {},
                onBack = {},
            )
        }
        waitForIdle()
    }

    @Test
    @Config(qualifiers = "vi")
    fun vietnamese_settingsUsesVietnameseText() =
        runComposeUiTest {
            showSettings(
                SettingsUiState(
                    loaded = true,
                    errors = SettingsFieldError.entries.toSet(),
                    connection = ConnectionTestState.Done(ConnectionTestResult.Unauthorized),
                ),
            )

            onNodeWithTag(SettingsScreenTags.TITLE).assertTextEquals("Cài đặt")
            onNodeWithTag(SettingsScreenTags.provider(LlmProviderType.OpenAICompatible)).assertTextEquals("Tương thích OpenAI")
            onNodeWithTag(SettingsScreenTags.ERROR_PREFIX + SettingsFieldError.ApiKeyBlank.name, useUnmergedTree = true)
                .assertTextEquals("Hãy nhập khóa API.")
            onNodeWithTag(SettingsScreenTags.CONNECTION_STATUS).performScrollTo().assertTextEquals("Khóa API bị từ chối.")
        }

    @Test
    @Config(qualifiers = "en")
    fun english_settingsUsesEnglishText() =
        runComposeUiTest {
            showSettings(SettingsUiState(loaded = true, errors = setOf(SettingsFieldError.ApiKeyBlank)))

            onNodeWithTag(SettingsScreenTags.TITLE).assertTextEquals("Settings")
            onNodeWithTag(SettingsScreenTags.provider(LlmProviderType.OpenAICompatible)).assertTextEquals("OpenAI-compatible")
            onNodeWithTag(SettingsScreenTags.ERROR_PREFIX + SettingsFieldError.ApiKeyBlank.name, useUnmergedTree = true).assertTextEquals("Enter an API key.")
        }

    @Test
    @Config(qualifiers = "vi")
    fun vietnamese_notConfiguredErrorPointsToSettings() =
        runComposeUiTest {
            setContent {
                AppContent(
                    uiState = AnalysisUiState.Error(AnalysisFailure.NotConfigured),
                    onAnalyze = { _, _, _ -> },
                    onAnalyzeAgain = {},
                    settingsRepository = InMemorySettingsRepository(),
                )
            }
            waitForIdle()

            onNodeWithTag(SetupScreenTags.ERROR)
                .assertTextEquals("Chưa cấu hình kết nối AI. Mở Cài đặt để hoàn tất cấu hình.")
            onNodeWithTag(SetupScreenTags.OPEN_SETTINGS).assertTextEquals("Cài đặt")
        }

    @Test
    @Config(qualifiers = "en")
    fun english_notConfiguredErrorPointsToSettings() =
        runComposeUiTest {
            setContent {
                AppContent(
                    uiState = AnalysisUiState.Error(AnalysisFailure.NotConfigured),
                    onAnalyze = { _, _, _ -> },
                    onAnalyzeAgain = {},
                    settingsRepository = InMemorySettingsRepository(),
                )
            }
            waitForIdle()

            onNodeWithTag(SetupScreenTags.ERROR)
                .assertTextEquals("The AI connection is not set up. Open Settings to finish setting it up.")
            onNodeWithTag(SetupScreenTags.OPEN_SETTINGS).assertTextEquals("Settings")
        }

    private fun ComposeUiTest.showAmbiguous(explanation: String) {
        setContent {
            AppContent(
                uiState = AnalysisUiState.Error(AnalysisFailure.AmbiguousSubject(explanation)),
                onAnalyze = { _, _, _ -> },
                onAnalyzeAgain = {},
                settingsRepository = InMemorySettingsRepository(),
            )
        }
        waitForIdle()
    }

    @Test
    @Config(qualifiers = "en")
    fun english_ambiguousSubjectShowsClarification() =
        runComposeUiTest {
            showAmbiguous("Candidates: A, B")

            onNodeWithTag(SetupScreenTags.CLARIFICATION).assertTextContains("Please clarify the subject")
            onNodeWithTag(SetupScreenTags.CLARIFICATION)
                .assertTextContains("add details such as the chain", substring = true, ignoreCase = true)
            onNodeWithTag(SetupScreenTags.CLARIFICATION_DETAIL, useUnmergedTree = true)
                .assertTextEquals("Candidates: A, B")
        }

    @Test
    @Config(qualifiers = "vi")
    fun vietnamese_ambiguousSubjectShowsClarification() =
        runComposeUiTest {
            showAmbiguous("Ứng viên: A, B")

            onNodeWithTag(SetupScreenTags.CLARIFICATION).assertTextContains("Vui lòng làm rõ đối tượng")
            onNodeWithTag(SetupScreenTags.CLARIFICATION).assertTextContains("Hãy bổ sung chi tiết", substring = true)
            onNodeWithTag(SetupScreenTags.CLARIFICATION_DETAIL, useUnmergedTree = true)
                .assertTextEquals("Ứng viên: A, B")
        }

    @Test
    @Config(qualifiers = "en")
    fun english_blankExplanationHasNoDetailAndNoDanglingColon() =
        runComposeUiTest {
            showAmbiguous("   ")

            onNodeWithTag(SetupScreenTags.CLARIFICATION).assertExists()
            onNodeWithTag(SetupScreenTags.CLARIFICATION_DETAIL, useUnmergedTree = true).assertDoesNotExist()
        }
}
