package com.tekome.vcman.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import com.tekome.vcman.domain.ProjectScoreReport
import com.tekome.vcman.domain.QuestionScoreResult
import com.tekome.vcman.domain.ScoreSectionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.stringResource
import vcman.sharedui.generated.resources.Res
import vcman.sharedui.generated.resources.decimal_separator
import vcman.sharedui.generated.resources.report_field_line
import vcman.sharedui.generated.resources.report_raw_score
import vcman.sharedui.generated.resources.report_score_fraction
import vcman.sharedui.generated.resources.report_weight
import vcman.sharedui.generated.resources.report_weighted

// --- Pure helper tests (no Compose runtime needed) ---

class ScoreReportScreenHelpersTest {
    @Test
    fun safeRatio_guardsZeroNegativeAndNonFinite() {
        assertEquals(0f, safeRatio(0.0, 0.0))
        assertEquals(0.5f, safeRatio(5.0, 10.0))
        assertEquals(1f, safeRatio(12.0, 10.0))
        assertEquals(0f, safeRatio(-1.0, 10.0))
        assertEquals(0f, safeRatio(Double.NaN, 10.0))
        assertEquals(0f, safeRatio(1.0, -5.0))
    }

    @Test
    fun formatScore_isPlatformIndependentAndTrimsTrailingZero() {
        assertEquals("7", formatScore(7.0, "."))
        assertEquals("7.5", formatScore(7.46, "."))
        assertEquals("7.4", formatScore(7.44, "."))
        assertEquals("0", formatScore(0.0, "."))
        assertEquals("-0.5", formatScore(-0.5, "."))
        assertEquals("13", formatScore(12.96, "."))
    }

    @Test
    fun formatScore_usesGivenDecimalSeparator() {
        assertEquals("7,5", formatScore(7.46, ","))
        assertEquals("-0,5", formatScore(-0.5, ","))
        assertEquals("7", formatScore(7.0, ","))
    }

    @Test
    fun defaultExpanded_opensOnlyTheFirstSection() {
        assertEquals(emptySet(), defaultExpanded(emptyList()))
        assertEquals(setOf(0), defaultExpanded(listOf(section("A"))))
        assertEquals(setOf(0), defaultExpanded(listOf(section("A"), section("B"), section("C"), section("D"))))
    }

    @Test
    fun toggle_addsRemovesAndRoundTrips() {
        val empty = emptySet<Int>()
        assertEquals(setOf(2), empty.toggle(2))
        assertEquals(empty, empty.toggle(2).toggle(2))
        assertEquals(setOf(1), setOf(1, 2).toggle(2))
    }

    private fun section(name: String) = ScoreSectionResult(name = name, questions = emptyList())
}

/**
 * Resolves the localized pieces the screen renders, from inside composition, so expectations track
 * whatever locale the host runs under. Exact per-locale text is asserted in `LocalizedUiTest`.
 */
private class ReportFormat {
    var separator = "."
    var rawLabel = ""
    var weightLabel = ""
    var weightedLabel = ""
    var fieldLineTemplate = ""
    var fractionTemplate = ""

    fun score(value: Double) = formatScore(value, separator)

    fun fraction(
        value: Double,
        max: Double,
    ) = fractionTemplate.replace("{0}", score(value)).replace("{1}", score(max))

    fun field(
        label: String,
        value: String,
    ) = fieldLineTemplate.replace("{0}", label).replace("{1}", value)
}

@Composable
private fun ReportFormat.Capture() {
    separator = stringResource(Res.string.decimal_separator)
    rawLabel = stringResource(Res.string.report_raw_score)
    weightLabel = stringResource(Res.string.report_weight)
    weightedLabel = stringResource(Res.string.report_weighted)
    fieldLineTemplate = stringResource(Res.string.report_field_line, "{0}", "{1}")
    fractionTemplate = stringResource(Res.string.report_score_fraction, "{0}", "{1}")
}

// --- Compose UI tests, over a table of report "shapes" (question count per section) ---

/** New shape = new row here, not a new test method. */
private val shapes =
    listOf(
        emptyList(),
        listOf(0),
        listOf(3),
        listOf(2, 0, 1, 4, 1, 3, 2),
    )

private fun reportWith(shape: List<Int>): ProjectScoreReport =
    ProjectScoreReport(
        subjectName = "Acme",
        rubricTitle = "Rubric",
        overallSummary = "Summary",
        generatedAtEpochMillis = 0L,
        sections =
            shape.mapIndexed { sectionIndex, questionCount ->
                ScoreSectionResult(
                    // Same name on purpose: sections carry no uniqueness guarantee from the LLM.
                    name = "Same name",
                    questions =
                        List(questionCount) { questionIndex ->
                            QuestionScoreResult(
                                id = "Q$questionIndex",
                                label = "Label $questionIndex",
                                // Deliberately varies per section/question index (not a fixed
                                // constant) so a test asserting against these values can't pass by
                                // coincidence if the screen swaps e.g. rawScore for weightedScore.
                                weight = 0.5 + (questionIndex % 4) * 0.5,
                                rawScore = ((sectionIndex * 3 + questionIndex * 2) % 9).toDouble(),
                                comment = "c$sectionIndex-$questionIndex",
                                sourceUrl = if (questionIndex % 2 == 0) "https://example.com/$sectionIndex/$questionIndex" else null,
                            )
                        },
                )
            },
    )

@OptIn(ExperimentalTestApi::class)
class ScoreReportScreenTest : ComposeUiTestRunner() {
    @Test
    fun rendersOneCardPerSection_forEveryShape() =
        runComposeUiTest {
            var report by mutableStateOf(reportWith(shapes.first()))
            setContent { ScoreReportScreen(report = report, onAnalyzeAgain = {}) }

            shapes.forEach { shape ->
                report = reportWith(shape)
                waitForIdle()
                shape.indices.forEach { i ->
                    onNodeWithTag(ScoreReportScreenTags.LIST)
                        .performScrollToNode(hasTestTag(ScoreReportScreenTags.section(i)))
                    onNodeWithTag(ScoreReportScreenTags.section(i)).assertExists()
                }
                onNodeWithTag(ScoreReportScreenTags.section(shape.size)).assertDoesNotExist()
            }
        }

    @Test
    fun header_matchesReportProperties() =
        runComposeUiTest {
            val report = reportWith(listOf(2, 3))
            val format = ReportFormat()
            setContent {
                format.Capture()
                ScoreReportScreen(report = report, onAnalyzeAgain = {})
            }

            onNodeWithTag(ScoreReportScreenTags.HEADER).assertExists()
            onNodeWithTag(ScoreReportScreenTags.HEADER_SCORE)
                .assertTextEquals("${format.score(report.grandTotal)} / ${format.score(report.grandMax)}")
            onNodeWithTag(ScoreReportScreenTags.HEADER_SUMMARY).assertTextEquals(report.overallSummary)
        }

    @Test
    fun sectionCard_showsScoreMatchingSectionProperties() =
        runComposeUiTest {
            val report = reportWith(listOf(2, 3))
            val format = ReportFormat()
            setContent {
                format.Capture()
                ScoreReportScreen(report = report, onAnalyzeAgain = {})
            }

            report.sections.forEachIndexed { i, section ->
                onNodeWithTag(ScoreReportScreenTags.LIST)
                    .performScrollToNode(hasTestTag(ScoreReportScreenTags.sectionScore(i)))
                onNodeWithTag(ScoreReportScreenTags.sectionScore(i))
                    .assertTextEquals("${format.score(section.total)} / ${format.score(section.maxPoints)}")
                onNodeWithTag(ScoreReportScreenTags.sectionProgress(i))
                    .assertRangeInfoEquals(ProgressBarRangeInfo(safeRatio(section.total, section.maxPoints), 0f..1f))
            }
        }

    @Test
    fun headerProgress_reflectsGrandTotalOverGrandMax() =
        runComposeUiTest {
            val report = reportWith(listOf(2, 3))
            setContent { ScoreReportScreen(report = report, onAnalyzeAgain = {}) }

            onNodeWithTag(ScoreReportScreenTags.HEADER_PROGRESS)
                .assertRangeInfoEquals(ProgressBarRangeInfo(safeRatio(report.grandTotal, report.grandMax), 0f..1f))
        }

    @Test
    fun sectionWithZeroMaxPoints_doesNotProduceNaNProgress() =
        runComposeUiTest {
            setContent { ScoreReportScreen(report = reportWith(listOf(0)), onAnalyzeAgain = {}) }

            onNodeWithTag(ScoreReportScreenTags.sectionProgress(0))
                .assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
        }

    @Test
    fun accordion_firstSectionOpenOthersClosed_toggleWorks() =
        runComposeUiTest {
            setContent { ScoreReportScreen(report = reportWith(listOf(2, 2)), onAnalyzeAgain = {}) }

            onNodeWithTag(ScoreReportScreenTags.sectionToggle(0)).assertIsOn()
            onNodeWithTag(ScoreReportScreenTags.LIST)
                .performScrollToNode(hasTestTag(ScoreReportScreenTags.sectionToggle(1)))
            onNodeWithTag(ScoreReportScreenTags.sectionToggle(1)).assertIsOff()
            onNodeWithTag(ScoreReportScreenTags.question(1, 0)).assertDoesNotExist()

            onNodeWithTag(ScoreReportScreenTags.sectionToggle(1)).performClick()
            waitForIdle()

            onNodeWithTag(ScoreReportScreenTags.sectionToggle(1)).assertIsOn()
            onNodeWithTag(ScoreReportScreenTags.LIST)
                .performScrollToNode(hasTestTag(ScoreReportScreenTags.question(1, 0)))
            onNodeWithTag(ScoreReportScreenTags.question(1, 0)).assertExists()

            onNodeWithTag(ScoreReportScreenTags.sectionToggle(1)).performClick()
            waitForIdle()
            onNodeWithTag(ScoreReportScreenTags.sectionToggle(1)).assertIsOff()
        }

    @Test
    fun questionRow_showsAllSevenFieldsAndSourceLinkWhenPresent() =
        runComposeUiTest {
            val report = reportWith(listOf(2))
            setContent { ScoreReportScreen(report = report, onAnalyzeAgain = {}) }

            // question 0: sourceUrl present (even index)
            onNodeWithTag(ScoreReportScreenTags.question(0, 0)).assertExists()
            onNodeWithTag(ScoreReportScreenTags.questionField(0, 0, "raw")).assertExists()
            onNodeWithTag(ScoreReportScreenTags.questionField(0, 0, "weight")).assertExists()
            onNodeWithTag(ScoreReportScreenTags.questionField(0, 0, "weighted")).assertExists()
            onNodeWithTag(ScoreReportScreenTags.questionField(0, 0, "comment")).assertExists()
            onNodeWithTag(ScoreReportScreenTags.sourceLink(0, 0)).assertExists()

            // question 1: sourceUrl == null (odd index) -> no link, no crash
            onNodeWithTag(ScoreReportScreenTags.sourceLink(0, 1)).assertDoesNotExist()
        }

    @Test
    fun questionFields_matchComputedProperties_acrossDifferentReportShapes() =
        runComposeUiTest {
            // Two structurally different reports (different question counts in section 0, and -
            // via reportWith's per-question weight/rawScore formula - different weight/rawScore
            // values), so a field mixup (e.g. rawScore swapped for weightedScore) can't pass by
            // coincidence (AC #8: values must match exactly across different report structures).
            var report by mutableStateOf(reportWith(shapes[2]))
            val format = ReportFormat()
            setContent {
                format.Capture()
                ScoreReportScreen(report = report, onAnalyzeAgain = {})
            }

            listOf(shapes[2], shapes.last()).forEach { shape ->
                report = reportWith(shape)
                waitForIdle()

                val section = report.sections.first()
                section.questions.forEachIndexed { questionIndex, question ->
                    onNodeWithTag(ScoreReportScreenTags.LIST)
                        .performScrollToNode(hasTestTag(ScoreReportScreenTags.question(0, questionIndex)))
                    onNodeWithTag(ScoreReportScreenTags.questionField(0, questionIndex, "raw"))
                        .assertTextEquals(
                            format.field(
                                format.rawLabel,
                                format.fraction(question.rawScore, QuestionScoreResult.MAX_RAW_SCORE),
                            ),
                        )
                    onNodeWithTag(ScoreReportScreenTags.questionField(0, questionIndex, "weight"))
                        .assertTextEquals(format.field(format.weightLabel, format.score(question.weight)))
                    onNodeWithTag(ScoreReportScreenTags.questionField(0, questionIndex, "weighted"))
                        .assertTextEquals(
                            format.field(
                                format.weightedLabel,
                                format.fraction(question.weightedScore, question.maxPoints),
                            ),
                        )
                }
            }
        }

    @Test
    fun sourceLink_opensUrlThroughUriHandler() =
        runComposeUiTest {
            val openedUrls = mutableListOf<String>()
            val fakeHandler =
                object : UriHandler {
                    override fun openUri(uri: String) {
                        openedUrls += uri
                    }
                }
            setContent {
                CompositionLocalProvider(LocalUriHandler provides fakeHandler) {
                    ScoreReportScreen(report = reportWith(listOf(1)), onAnalyzeAgain = {})
                }
            }

            onNodeWithTag(ScoreReportScreenTags.sourceLink(0, 0)).performClick()

            assertEquals(listOf("https://example.com/0/0"), openedUrls)
        }

    @Test
    fun emptyReport_rendersSafely_withoutSections() =
        runComposeUiTest {
            setContent { ScoreReportScreen(report = reportWith(emptyList()), onAnalyzeAgain = {}) }

            onNodeWithTag(ScoreReportScreenTags.HEADER).assertExists()
            onNodeWithTag(ScoreReportScreenTags.ANALYZE_AGAIN).assertExists()
            onNodeWithTag(ScoreReportScreenTags.section(0)).assertDoesNotExist()
        }

    @Test
    fun sectionWithNoQuestions_showsEmptyMessage_whenExpanded() =
        runComposeUiTest {
            setContent { ScoreReportScreen(report = reportWith(listOf(0)), onAnalyzeAgain = {}) }

            onNodeWithTag(ScoreReportScreenTags.sectionEmpty(0)).assertExists()
        }

    // Name scoped to what's actually exercised: "Analyze again" lives in a fixed item(key =
    // "actions") independent of report.sections, so its shape-independence is a code-structure
    // guarantee, not something this test needs to re-verify across every shape in the table.
    @Test
    fun analyzeAgain_firesExactlyOnceForEachClick_withEmptyReport() =
        runComposeUiTest {
            var calls = 0
            setContent { ScoreReportScreen(report = reportWith(emptyList()), onAnalyzeAgain = { calls++ }) }

            onNodeWithTag(ScoreReportScreenTags.ANALYZE_AGAIN).performClick()
            assertEquals(1, calls)

            onNodeWithTag(ScoreReportScreenTags.ANALYZE_AGAIN).performClick()
            assertEquals(2, calls)
        }

    @Test
    fun newReport_resetsAccordionToDefault() =
        runComposeUiTest {
            var report by mutableStateOf(reportWith(listOf(1, 1)))
            setContent { ScoreReportScreen(report = report, onAnalyzeAgain = {}) }

            onNodeWithTag(ScoreReportScreenTags.LIST)
                .performScrollToNode(hasTestTag(ScoreReportScreenTags.sectionToggle(1)))
            onNodeWithTag(ScoreReportScreenTags.sectionToggle(1)).performClick()
            waitForIdle()
            onNodeWithTag(ScoreReportScreenTags.sectionToggle(1)).assertIsOn()

            // A genuinely new report (not just an equal one) must reset the accordion.
            report = report.copy(subjectName = "Another subject")
            waitForIdle()

            onNodeWithTag(ScoreReportScreenTags.sectionToggle(0)).assertIsOn()
            onNodeWithTag(ScoreReportScreenTags.LIST)
                .performScrollToNode(hasTestTag(ScoreReportScreenTags.sectionToggle(1)))
            onNodeWithTag(ScoreReportScreenTags.sectionToggle(1)).assertIsOff()
        }
}
