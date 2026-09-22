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
                weightedScore = 12.0,
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
                weightedScore = 18.0,
                comment = "Founders have previous exits",
                sourceUrl = "https://linkedin.com/in/founder",
            )
        assertEquals("https://linkedin.com/in/founder", question.sourceUrl)
    }

    @Test
    fun scoreSectionResult_emptyQuestions_totalIsZero() {
        val section =
            ScoreSectionResult(
                name = "Product Vision",
                maxPoints = 25.0,
                questions = emptyList(),
            )
        assertEquals("Product Vision", section.name)
        assertEquals(25.0, section.maxPoints)
        assertEquals(0.0, section.total)
    }

    @Test
    fun scoreSectionResult_totalCalculatesSumOfWeightedScores() {
        val q1 =
            QuestionScoreResult(
                id = "q1",
                label = "Problem Statement",
                weight = 1.0,
                rawScore = 8.0,
                weightedScore = 8.0,
                comment = "Clear pain point",
            )
        val q2 =
            QuestionScoreResult(
                id = "q2",
                label = "Unique Value Proposition",
                weight = 1.5,
                rawScore = 10.0,
                weightedScore = 15.0,
                comment = "Defensible moat",
            )
        val section =
            ScoreSectionResult(
                name = "Value Proposition",
                maxPoints = 30.0,
                questions = listOf(q1, q2),
            )
        assertEquals(23.0, section.total)
    }

    @Test
    fun scoreSectionResult_copy_preservesDynamicTotalComputation() {
        val q1 =
            QuestionScoreResult(
                id = "q1",
                label = "Traction",
                weight = 1.0,
                rawScore = 7.0,
                weightedScore = 7.0,
                comment = "Good MRR growth",
            )
        val q2 =
            QuestionScoreResult(
                id = "q2",
                label = "Retention",
                weight = 2.0,
                rawScore = 6.0,
                weightedScore = 12.0,
                comment = "Acceptable cohort retention",
            )
        val section =
            ScoreSectionResult(
                name = "Traction & Retention",
                maxPoints = 25.0,
                questions = listOf(q1, q2),
            )
        assertEquals(19.0, section.total)

        val updated = section.copy(questions = listOf(q1))
        assertEquals(7.0, updated.total)
    }

    @Test
    fun projectScoreReport_supportsArbitraryDynamicSectionsAndQuestions() {
        val qA =
            QuestionScoreResult(
                id = "custom_1",
                label = "Custom Metric Alpha",
                weight = 1.0,
                rawScore = 9.0,
                weightedScore = 9.0,
                comment = "Excellent",
            )
        val sectionA =
            ScoreSectionResult(
                name = "Custom Section A",
                maxPoints = 10.0,
                questions = listOf(qA),
            )

        val qB =
            QuestionScoreResult(
                id = "custom_2",
                label = "Custom Metric Beta",
                weight = 2.0,
                rawScore = 8.5,
                weightedScore = 17.0,
                comment = "Solid",
            )
        val sectionB =
            ScoreSectionResult(
                name = "Custom Section B",
                maxPoints = 20.0,
                questions = listOf(qB),
            )

        val report =
            ProjectScoreReport(
                subjectName = "Acme Corp",
                rubricTitle = "Dynamic Evaluation Rubric v2",
                overallSummary = "Strong overall execution across both custom areas.",
                sections = listOf(sectionA, sectionB),
                grandTotal = 26.0,
                grandMax = 30.0,
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
        assertEquals(17.0, report.sections[1].total)
    }
}
