package com.tekome.vcman.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tekome.vcman.domain.ProjectScoreReport
import com.tekome.vcman.domain.QuestionScoreResult
import com.tekome.vcman.domain.ScoreSectionResult
import kotlin.math.abs
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import vcman.sharedui.generated.resources.Res
import vcman.sharedui.generated.resources.decimal_separator
import vcman.sharedui.generated.resources.report_analyze_again
import vcman.sharedui.generated.resources.report_comment
import vcman.sharedui.generated.resources.report_field_line
import vcman.sharedui.generated.resources.report_no_questions
import vcman.sharedui.generated.resources.report_question_title
import vcman.sharedui.generated.resources.report_raw_score
import vcman.sharedui.generated.resources.report_score_fraction
import vcman.sharedui.generated.resources.report_source
import vcman.sharedui.generated.resources.report_weight
import vcman.sharedui.generated.resources.report_weighted

internal object ScoreReportScreenTags {
    const val LIST = "score_report_list"
    const val HEADER = "score_report_header"
    const val HEADER_SCORE = "score_report_header_score"
    const val HEADER_PROGRESS = "score_report_header_progress"
    const val HEADER_SUMMARY = "score_report_header_summary"
    const val ANALYZE_AGAIN = "score_report_analyze_again"

    fun section(sectionIndex: Int) = "score_report_section_$sectionIndex"

    fun sectionToggle(sectionIndex: Int) = "score_report_section_${sectionIndex}_toggle"

    fun sectionScore(sectionIndex: Int) = "score_report_section_${sectionIndex}_score"

    fun sectionProgress(sectionIndex: Int) = "score_report_section_${sectionIndex}_progress"

    fun sectionEmpty(sectionIndex: Int) = "score_report_section_${sectionIndex}_empty"

    fun question(
        sectionIndex: Int,
        questionIndex: Int,
    ) = "score_report_question_${sectionIndex}_$questionIndex"

    fun questionField(
        sectionIndex: Int,
        questionIndex: Int,
        fieldKey: String,
    ) = "score_report_question_${sectionIndex}_${questionIndex}_$fieldKey"

    fun sourceLink(
        sectionIndex: Int,
        questionIndex: Int,
    ) = "score_report_source_${sectionIndex}_$questionIndex"
}

private const val DEFAULT_EXPANDED_COUNT = 1

/**
 * Which section indices are expanded by default: the first [DEFAULT_EXPANDED_COUNT] sections.
 * An empty `sections` list naturally yields an empty set - no size check needed.
 */
internal fun defaultExpanded(sections: List<ScoreSectionResult>): Set<Int> = sections.indices.take(DEFAULT_EXPANDED_COUNT).toSet()

internal fun Set<Int>.toggle(index: Int): Set<Int> = if (index in this) this - index else this + index

/**
 * Ratio of [value] over [max] for a progress indicator, guarded against division by zero,
 * `NaN` and negative inputs (e.g. a section/report with zero max points).
 */
internal fun safeRatio(
    value: Double,
    max: Double,
): Float = if (max > 0.0 && value.isFinite()) (value / max).toFloat().coerceIn(0f, 1f) else 0f

/**
 * Minimal score formatting: at most one decimal digit, with [decimalSeparator] taken from the
 * `decimal_separator` string resource (en ".", vi ","). Not a full CLDR number formatter: no
 * grouping separators, digits or scripts of other locales.
 */
internal fun formatScore(
    value: Double,
    decimalSeparator: String,
): String {
    if (!value.isFinite()) return "-"
    val tenths = (value * 10).roundToLong()
    val sign = if (tenths < 0) "-" else ""
    val absTenths = abs(tenths)
    return if (absTenths % 10 == 0L) {
        "$sign${absTenths / 10}"
    } else {
        "$sign${absTenths / 10}$decimalSeparator${absTenths % 10}"
    }
}

private val expandedSectionsSaver =
    listSaver<Set<Int>, Int>(
        save = { it.toList() },
        restore = { it.toSet() },
    )

@Composable
fun ScoreReportScreen(
    modifier: Modifier = Modifier,
    report: ProjectScoreReport,
    onAnalyzeAgain: () -> Unit,
) {
    var expandedSections by
        rememberSaveable(report, stateSaver = expandedSectionsSaver) {
            mutableStateOf(defaultExpanded(report.sections))
        }

    val decimalSeparator = stringResource(Res.string.decimal_separator)
    val fmt: (Double) -> String = remember(decimalSeparator) { { formatScore(it, decimalSeparator) } }

    LazyColumn(modifier = modifier.fillMaxSize().testTag(ScoreReportScreenTags.LIST)) {
        item(key = "header") { ReportHeader(report, fmt) }
        itemsIndexed(
            items = report.sections,
            key = { index, _ -> "section-$index" },
        ) { index, section ->
            SectionCard(
                sectionIndex = index,
                section = section,
                fmt = fmt,
                expanded = index in expandedSections,
                onToggle = { expandedSections = expandedSections.toggle(index) },
            )
        }
        item(key = "actions") {
            Button(
                onClick = onAnalyzeAgain,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(ScoreReportScreenTags.ANALYZE_AGAIN),
            ) {
                Text(stringResource(Res.string.report_analyze_again))
            }
        }
    }
}

@Composable
private fun ReportHeader(
    report: ProjectScoreReport,
    fmt: (Double) -> String,
) {
    val score = scoreFraction(report.grandTotal, report.grandMax, fmt)
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(ScoreReportScreenTags.HEADER),
    ) {
        Text(report.subjectName, style = MaterialTheme.typography.headlineSmall)
        Text(
            score,
            modifier = Modifier.testTag(ScoreReportScreenTags.HEADER_SCORE),
        )
        LinearProgressIndicator(
            progress = { safeRatio(report.grandTotal, report.grandMax) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .semantics {
                        stateDescription = score
                    }.testTag(ScoreReportScreenTags.HEADER_PROGRESS),
        )
        Text(report.overallSummary, modifier = Modifier.testTag(ScoreReportScreenTags.HEADER_SUMMARY))
    }
}

@Composable
private fun SectionCard(
    sectionIndex: Int,
    section: ScoreSectionResult,
    expanded: Boolean,
    onToggle: () -> Unit,
    fmt: (Double) -> String,
) {
    val score = scoreFraction(section.total, section.maxPoints, fmt)
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(ScoreReportScreenTags.section(sectionIndex)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = expanded, role = Role.Button, onValueChange = { onToggle() })
                    .padding(16.dp)
                    .testTag(ScoreReportScreenTags.sectionToggle(sectionIndex)),
        ) {
            Text(section.name, modifier = Modifier.fillMaxWidth())
        }
        Text(
            score,
            modifier = Modifier.padding(horizontal = 16.dp).testTag(ScoreReportScreenTags.sectionScore(sectionIndex)),
        )
        LinearProgressIndicator(
            progress = { safeRatio(section.total, section.maxPoints) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .semantics {
                        stateDescription = score
                    }.testTag(ScoreReportScreenTags.sectionProgress(sectionIndex)),
        )
        if (expanded) {
            if (section.questions.isEmpty()) {
                Text(
                    stringResource(Res.string.report_no_questions),
                    modifier = Modifier.padding(16.dp).testTag(ScoreReportScreenTags.sectionEmpty(sectionIndex)),
                )
            } else {
                section.questions.forEachIndexed { questionIndex, question ->
                    QuestionRow(sectionIndex, questionIndex, question, fmt)
                }
            }
        }
    }
}

/** Resolves the localized "value / max" text; the template (separator, order) lives in resources. */
@Composable
private fun scoreFraction(
    value: Double,
    max: Double,
    fmt: (Double) -> String,
): String = stringResource(Res.string.report_score_fraction, fmt(value), fmt(max))

private data class QuestionField(
    val key: String,
    val label: StringResource,
    val value: @Composable (QuestionScoreResult, (Double) -> String) -> String,
)

private val questionFields =
    listOf(
        QuestionField(key = "raw", label = Res.string.report_raw_score) { question, fmt ->
            scoreFraction(question.rawScore, QuestionScoreResult.MAX_RAW_SCORE, fmt)
        },
        QuestionField(key = "weight", label = Res.string.report_weight) { question, fmt -> fmt(question.weight) },
        QuestionField(key = "weighted", label = Res.string.report_weighted) { question, fmt ->
            scoreFraction(question.weightedScore, question.maxPoints, fmt)
        },
        QuestionField(key = "comment", label = Res.string.report_comment) { question, _ -> question.comment },
    )

@Composable
private fun QuestionRow(
    sectionIndex: Int,
    questionIndex: Int,
    question: QuestionScoreResult,
    fmt: (Double) -> String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(ScoreReportScreenTags.question(sectionIndex, questionIndex)),
    ) {
        Text(stringResource(Res.string.report_question_title, question.id, question.label), fontWeight = FontWeight.SemiBold)
        questionFields.forEach { field ->
            Text(
                stringResource(Res.string.report_field_line, stringResource(field.label), field.value(question, fmt)),
                modifier =
                    Modifier.testTag(
                        ScoreReportScreenTags.questionField(sectionIndex, questionIndex, field.key),
                    ),
            )
        }
        question.sourceUrl?.takeIf { it.isNotBlank() }?.let { url ->
            val uriHandler = LocalUriHandler.current
            TextButton(
                onClick = { runCatching { uriHandler.openUri(url) } },
                modifier = Modifier.testTag(ScoreReportScreenTags.sourceLink(sectionIndex, questionIndex)),
            ) {
                Text(stringResource(Res.string.report_source))
            }
        }
    }
}

private val previewReport =
    ProjectScoreReport(
        subjectName = "Acme Protocol",
        rubricTitle = "Sample Rubric",
        overallSummary = "Solid fundamentals with some unverifiable claims.",
        generatedAtEpochMillis = 0L,
        sections =
            listOf(
                ScoreSectionResult(
                    name = "Value",
                    questions =
                        listOf(
                            QuestionScoreResult(
                                id = "V1",
                                label = "Whitepaper exists",
                                weight = 3.0,
                                rawScore = 8.0,
                                comment = "Clear technical whitepaper published.",
                                sourceUrl = "https://example.com/whitepaper",
                            ),
                            QuestionScoreResult(
                                id = "V2",
                                label = "Mainnet activity",
                                weight = 2.0,
                                rawScore = 6.0,
                                comment = "Active but low transaction volume.",
                                sourceUrl = null,
                            ),
                        ),
                ),
                ScoreSectionResult(name = "Community", questions = emptyList()),
                ScoreSectionResult(
                    name = "Need",
                    questions =
                        listOf(
                            QuestionScoreResult(
                                id = "N1",
                                label = "Problem legitimacy",
                                weight = 2.5,
                                rawScore = 9.0,
                                comment = "Well documented market need.",
                                sourceUrl = "https://example.com/market",
                            ),
                        ),
                ),
            ),
    )

@Composable
@Preview
private fun ScoreReportScreenPreview() {
    MaterialTheme {
        ScoreReportScreen(report = previewReport, onAnalyzeAgain = {})
    }
}

private val emptyPreviewReport =
    previewReport.copy(
        subjectName = "Empty Protocol",
        overallSummary = "No sections were produced for this rubric/subject.",
        sections = emptyList(),
    )

@Composable
@Preview
private fun ScoreReportScreenEmptyPreview() {
    MaterialTheme {
        ScoreReportScreen(report = emptyPreviewReport, onAnalyzeAgain = {})
    }
}
