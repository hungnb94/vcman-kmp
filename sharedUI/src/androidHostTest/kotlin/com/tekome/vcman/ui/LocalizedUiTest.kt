package com.tekome.vcman.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tekome.vcman.AppContent
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
                onAnalyze = { _, _, _, _ -> },
                onAnalyzeAgain = {},
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
}
