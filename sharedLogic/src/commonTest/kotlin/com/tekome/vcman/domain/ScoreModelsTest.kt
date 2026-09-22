package com.tekome.vcman.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScoreModelsTest {
    @Test
    fun rubricInput_holdsTitleAndText() {
        val input =
            RubricInput(
                title = "VC Pitch Rubric",
                text = "Section A: Market Size (Weight 2.0)...",
            )
        assertEquals("VC Pitch Rubric", input.title)
        assertEquals("Section A: Market Size (Weight 2.0)...", input.text)
    }

    @Test
    fun questionScoreResult_defaultsSourceUrlToNull() {
        val question =
            QuestionScoreResult(
                id = "q1",
                label = "Market Size",
                weight = 1.5,
                rawScore = 8.0,
                comment = "Strong TAM analysis",
            )
        assertEquals("q1", question.id)
        assertEquals("Market Size", question.label)
        assertEquals(1.5, question.weight)
        assertEquals(8.0, question.rawScore)
        assertEquals(12.0, question.weightedScore)
        assertEquals("Strong TAM analysis", question.comment)
        assertNull(question.sourceUrl)
    }

    @Test
    fun questionScoreResult_acceptsOptionalSourceUrl() {
        val question =
            QuestionScoreResult(
                id = "q2",
                label = "Team Track Record",
                weight = 2.0,
                rawScore = 9.0,
                comment = "Founders have previous exits",
                sourceUrl = "https://linkedin.com/in/founder",
            )
        assertEquals("https://linkedin.com/in/founder", question.sourceUrl)
        assertEquals(18.0, question.weightedScore)
    }

    @Test
    fun questionScoreResult_calculatesWeightedScoreAutomaticallyAndHandlesZero() {
        val zeroScore =
            QuestionScoreResult(
                id = "q_zero_score",
                label = "Zero Score Metric",
                weight = 1.5,
                rawScore = 0.0,
                comment = "Unsatisfactory",
            )
        assertEquals(0.0, zeroScore.weightedScore)

        val zeroWeight =
            QuestionScoreResult(
                id = "q_zero_weight",
                label = "Survey Only Metric",
                weight = 0.0,
                rawScore = 8.0,
                comment = "Informational only",
            )
        assertEquals(0.0, zeroWeight.weightedScore)
    }

    @Test
    fun questionScoreResult_copy_preservesDynamicWeightedScoreComputation() {
        val original =
            QuestionScoreResult(
                id = "q_dyn",
                label = "Unit Economics",
                weight = 1.5,
                rawScore = 8.0,
                comment = "Initial estimate",
            )
        assertEquals(12.0, original.weightedScore)

        val updatedScore = original.copy(rawScore = 6.0)
        assertEquals(9.0, updatedScore.weightedScore)

        val updatedWeight = original.copy(weight = 2.0)
        assertEquals(16.0, updatedWeight.weightedScore)
    }

    @Test
    fun scoreSectionResult_emptyQuestions_totalAndMaxPointsAreZero() {
        val section =
            ScoreSectionResult(
                name = "Product Vision",
                questions = emptyList(),
            )
        assertEquals("Product Vision", section.name)
        assertEquals(0.0, section.maxPoints)
        assertEquals(0.0, section.total)
    }

    @Test
    fun scoreSectionResult_totalAndMaxPointsCalculateDynamically() {
        val q1 =
            QuestionScoreResult(
                id = "q1",
                label = "Problem Statement",
                weight = 1.0,
                rawScore = 8.0,
                comment = "Clear pain point",
            )
        val q2 =
            QuestionScoreResult(
                id = "q2",
                label = "Unique Value Proposition",
                weight = 1.5,
                rawScore = 10.0,
                comment = "Defensible moat",
            )
        val section =
            ScoreSectionResult(
                name = "Value Proposition",
                questions = listOf(q1, q2),
            )
        assertEquals(23.0, section.total)
        assertEquals(25.0, section.maxPoints)
    }

    @Test
    fun scoreSectionResult_copy_preservesDynamicTotalAndMaxPointsComputation() {
        val q1 =
            QuestionScoreResult(
                id = "q1",
                label = "Traction",
                weight = 1.0,
                rawScore = 7.0,
                comment = "Good MRR growth",
            )
        val q2 =
            QuestionScoreResult(
                id = "q2",
                label = "Retention",
                weight = 2.0,
                rawScore = 6.0,
                comment = "Acceptable cohort retention",
            )
        val section =
            ScoreSectionResult(
                name = "Traction & Retention",
                questions = listOf(q1, q2),
            )
        assertEquals(19.0, section.total)
        assertEquals(30.0, section.maxPoints)

        val updated = section.copy(questions = listOf(q1))
        assertEquals(7.0, updated.total)
        assertEquals(10.0, updated.maxPoints)
    }

    @Test
    fun projectScoreReport_supportsArbitraryDynamicSectionsAndQuestions() {
        val qA =
            QuestionScoreResult(
                id = "custom_1",
                label = "Custom Metric Alpha",
                weight = 1.0,
                rawScore = 9.0,
                comment = "Excellent",
            )
        val sectionA =
            ScoreSectionResult(
                name = "Custom Section A",
                questions = listOf(qA),
            )

        val qB =
            QuestionScoreResult(
                id = "custom_2",
                label = "Custom Metric Beta",
                weight = 2.0,
                rawScore = 8.5,
                comment = "Solid",
            )
        val sectionB =
            ScoreSectionResult(
                name = "Custom Section B",
                questions = listOf(qB),
            )

        val report =
            ProjectScoreReport(
                subjectName = "Acme Corp",
                rubricTitle = "Dynamic Evaluation Rubric v2",
                overallSummary = "Strong overall execution across both custom areas.",
                sections = listOf(sectionA, sectionB),
                generatedAtEpochMillis = 1774200000000L,
            )

        assertEquals("Acme Corp", report.subjectName)
        assertEquals("Dynamic Evaluation Rubric v2", report.rubricTitle)
        assertEquals("Strong overall execution across both custom areas.", report.overallSummary)
        assertEquals(2, report.sections.size)
        assertEquals(26.0, report.grandTotal)
        assertEquals(30.0, report.grandMax)
        assertEquals(1774200000000L, report.generatedAtEpochMillis)
        assertEquals(9.0, report.sections[0].total)
        assertEquals(10.0, report.sections[0].maxPoints)
        assertEquals(17.0, report.sections[1].total)
        assertEquals(20.0, report.sections[1].maxPoints)
    }

    @Test
    fun projectScoreReport_emptySections_grandTotalAndGrandMaxAreZero() {
        val emptyReport =
            ProjectScoreReport(
                subjectName = "Ambiguous Subject Query",
                rubricTitle = "Standard Rubric",
                overallSummary = "Query is ambiguous; ticker collision detected.",
                sections = emptyList(),
                generatedAtEpochMillis = 1774200000000L,
            )

        assertEquals(0.0, emptyReport.grandTotal)
        assertEquals(0.0, emptyReport.grandMax)
    }

    @Test
    fun projectScoreReport_copy_preservesDynamicTotalsComputation() {
        val q =
            QuestionScoreResult(
                id = "q1",
                label = "Market",
                weight = 2.0,
                rawScore = 5.0,
                comment = "Average",
            )
        val section =
            ScoreSectionResult(
                name = "Market",
                questions = listOf(q),
            )
        val report =
            ProjectScoreReport(
                subjectName = "Startup X",
                rubricTitle = "Standard Rubric",
                overallSummary = "Initial draft",
                sections = listOf(section),
                generatedAtEpochMillis = 1774200000000L,
            )

        assertEquals(10.0, report.grandTotal)
        assertEquals(20.0, report.grandMax)

        val clearedReport = report.copy(sections = emptyList())
        assertEquals(0.0, clearedReport.grandTotal)
        assertEquals(0.0, clearedReport.grandMax)
    }
}
