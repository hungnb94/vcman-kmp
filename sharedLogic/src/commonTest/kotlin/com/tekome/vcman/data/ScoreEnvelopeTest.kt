package com.tekome.vcman.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class ScoreEnvelopeTest {
    private val validEnvelope =
        """
        {
          "subjectName": "Acme Inc",
          "overallSummary": "Solid fundamentals",
          "sections": [
            {
              "name": "Team",
              "questions": [
                {"id": "q1", "label": "Founder experience", "weight": 2.0, "rawScore": 8.5, "comment": "Strong", "sourceUrl": "https://example.com/a"},
                {"id": "q2", "label": "Team size", "weight": 1.0, "rawScore": 6.0, "comment": "OK", "sourceUrl": null}
              ]
            }
          ]
        }
        """.trimIndent()

    @Test
    fun decodeScoreReport_mapsEveryFieldOneToOne() {
        val report = decodeScoreReport(validEnvelope, rubricTitle = "My Rubric", generatedAtEpochMillis = 123L)

        assertEquals("Acme Inc", report.subjectName)
        assertEquals("My Rubric", report.rubricTitle)
        assertEquals("Solid fundamentals", report.overallSummary)
        assertEquals(123L, report.generatedAtEpochMillis)

        val section = report.sections.single()
        assertEquals("Team", section.name)
        assertEquals(2, section.questions.size)

        val q1 = section.questions[0]
        assertEquals("q1", q1.id)
        assertEquals("Founder experience", q1.label)
        assertEquals(2.0, q1.weight)
        assertEquals(8.5, q1.rawScore)
        assertEquals("Strong", q1.comment)
        assertEquals("https://example.com/a", q1.sourceUrl)

        assertNull(section.questions[1].sourceUrl)
    }

    @Test
    fun decodeScoreReport_toleratesCodeFenceAndPreamble() {
        val wrapped = "Sure, here is the analysis:\n```json\n$validEnvelope\n```\nHope that helps!"

        val report = decodeScoreReport(wrapped, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)

        assertEquals("Acme Inc", report.subjectName)
    }

    @Test
    fun decodeScoreReport_ignoresUnknownKeys() {
        val withExtra =
            """
            {"subjectName":"X","overallSummary":"Y","unexpectedField":"noise","sections":[
                {"name":"S","questions":[
                    {"id":"q","label":"L","weight":1.0,"rawScore":1.0,"comment":"C","extraField":true}
                ]}
            ]}
            """.trimIndent()

        val report = decodeScoreReport(withExtra, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)

        assertEquals("X", report.subjectName)
    }

    @Test
    fun decodeScoreReport_emptySectionsBecomesAmbiguousSubjectWithSummary() {
        val raw = """{"subjectName":"X","overallSummary":"Ambiguous: X Inc vs X Corp","sections":[]}"""

        val exception =
            assertFailsWith<AnalysisException> {
                decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)
            }

        val error = assertIs<AnalysisError.AmbiguousSubject>(exception.error)
        assertEquals("Ambiguous: X Inc vs X Corp", error.explanation)
    }

    @Test
    fun decodeScoreReport_missingRequiredFieldIsInvalidResponse() {
        val raw = """{"subjectName":"X"}"""

        val exception =
            assertFailsWith<AnalysisException> {
                decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)
            }

        assertIs<AnalysisError.InvalidResponse>(exception.error)
    }

    @Test
    fun decodeScoreReport_nonJsonIsInvalidResponse() {
        val exception =
            assertFailsWith<AnalysisException> {
                decodeScoreReport("I'm sorry, I cannot help with that.", rubricTitle = "Rubric", generatedAtEpochMillis = 0L)
            }

        assertIs<AnalysisError.InvalidResponse>(exception.error)
    }

    @Test
    fun decodeScoreReport_wrongTypeIsInvalidResponse() {
        val raw =
            """
            {"subjectName":"X","overallSummary":"Y","sections":[
                {"name":"S","questions":[
                    {"id":"q","label":"L","weight":"not-a-number","rawScore":1.0,"comment":"C"}
                ]}
            ]}
            """.trimIndent()

        val exception =
            assertFailsWith<AnalysisException> {
                decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)
            }

        assertIs<AnalysisError.InvalidResponse>(exception.error)
    }

    @Test
    fun decodeScoreReport_preamblePostambleWithBracesOutsideCodeFence() {
        val raw = "Preamble {not JSON} here\n$validEnvelope\nPostamble {also not JSON}"

        val report = decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)

        assertEquals("Acme Inc", report.subjectName)
    }

    @Test
    fun decodeScoreReport_codeFenceWithPreamblePostambleContainingBraces() {
        val raw = "```json\n{ \"ignored\": { \"nested\": \"object\" } }\n$validEnvelope\n```"

        val report = decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)

        assertEquals("Acme Inc", report.subjectName)
    }

    @Test
    fun decodeScoreReport_jsonWithEscapedQuotesAndBracesInStringValues() {
        val raw =
            """
            {
              "subjectName": "Test",
              "overallSummary": "Summary",
              "sections": [
                {
                  "name": "S",
                  "questions": [
                    {
                      "id": "q1",
                      "label": "L",
                      "weight": 1.0,
                      "rawScore": 5.0,
                      "comment": "Value with \"escaped quotes\" and {braces} inside",
                      "sourceUrl": null
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val report = decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)

        assertEquals("Test", report.subjectName)
        assertEquals(
            "Value with \"escaped quotes\" and {braces} inside",
            report.sections
                .single()
                .questions
                .single()
                .comment,
        )
    }

    @Test
    fun decodeScoreReport_multipleJsonCandidatesPicksLargest() {
        val raw = "Small {}\n$validEnvelope\nExtra { small: 1 }"

        val report = decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)

        assertEquals("Acme Inc", report.subjectName)
    }

    @Test
    fun decodeScoreReport_emptyObjectBecomesInvalidResponse() {
        val raw = "{}"

        val exception =
            assertFailsWith<AnalysisException> {
                decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)
            }

        assertIs<AnalysisError.InvalidResponse>(exception.error)
    }

    @Test
    fun decodeScoreReport_unbalancedBraceInPreambleStillFindsEnvelope() {
        val raw = "Scores use the {0-10 scale, see below:\n$validEnvelope"

        val report = decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)

        assertEquals("Acme Inc", report.subjectName)
    }

    @Test
    fun decodeScoreReport_missingSectionsKeyIsInvalidResponseNotAmbiguous() {
        val raw = """{"subjectName":"X","overallSummary":"Truncated"}"""

        val exception =
            assertFailsWith<AnalysisException> {
                decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)
            }

        assertIs<AnalysisError.InvalidResponse>(exception.error)
    }

    @Test
    fun decodeScoreReport_rawScoreAboveMaxIsInvalidResponse() {
        val raw =
            """
            {"subjectName":"X","overallSummary":"Y","sections":[
                {"name":"S","questions":[
                    {"id":"q","label":"L","weight":1.0,"rawScore":85.0,"comment":"C"}
                ]}
            ]}
            """.trimIndent()

        val exception =
            assertFailsWith<AnalysisException> {
                decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)
            }

        assertIs<AnalysisError.InvalidResponse>(exception.error)
    }

    @Test
    fun decodeScoreReport_negativeWeightIsInvalidResponse() {
        val raw =
            """
            {"subjectName":"X","overallSummary":"Y","sections":[
                {"name":"S","questions":[
                    {"id":"q","label":"L","weight":-1.0,"rawScore":5.0,"comment":"C"}
                ]}
            ]}
            """.trimIndent()

        val exception =
            assertFailsWith<AnalysisException> {
                decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)
            }

        assertIs<AnalysisError.InvalidResponse>(exception.error)
    }

    @Test
    fun decodeScoreReport_largerNonEnvelopeObjectDoesNotHideEnvelope() {
        val echoedInput = """{"rubric": {"title": "${"x".repeat(2_000)}", "questions": [{"id": "q1"}]}}"""
        val raw = "Input was:\n$echoedInput\nResult:\n$validEnvelope"

        val report = decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)

        assertEquals("Acme Inc", report.subjectName)
    }

    @Test
    fun decodeScoreReport_fencedNonEnvelopeFallsBackToUnfencedEnvelope() {
        val raw = "Example format:\n```json\n{\"example\": true}\n```\n$validEnvelope"

        val report = decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)

        assertEquals("Acme Inc", report.subjectName)
    }

    @Test
    fun decodeScoreReport_falseBalancedMatchInProseDoesNotSwallowEnvelope() {
        val raw = "Note {\"quote} here\n" + validEnvelope.replace("\"Strong\"", "\"Strong } lead\"")

        val report = decodeScoreReport(raw, rubricTitle = "Rubric", generatedAtEpochMillis = 0L)

        assertEquals("Acme Inc", report.subjectName)
    }
}
