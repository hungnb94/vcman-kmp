package com.tekome.vcman.data

import com.tekome.vcman.domain.RubricInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptBuilderTest {
    @Test
    fun buildSystemPrompt_embedsVerbatimRubricTitleAndTextInsideXmlTags() {
        val rubric =
            RubricInput(
                title = "Seed VC Rubric",
                text = "1. Founding Team (Weight: 2.0)\n2. Market Opportunity (Weight: 1.5)",
            )
        val prompt = PromptBuilder.buildSystemPrompt(rubric)

        assertTrue(
            prompt.contains("<rubric_title>\nSeed VC Rubric\n</rubric_title>"),
            "System prompt must contain rubric title inside <rubric_title> tag",
        )
        assertTrue(
            prompt.contains(
                "<rubric_text>\n1. Founding Team (Weight: 2.0)\n2. Market Opportunity (Weight: 1.5)\n</rubric_text>",
            ),
            "System prompt must contain verbatim rubric text inside <rubric_text> tag",
        )
    }

    @Test
    fun buildSystemPrompt_includesDynamicInferenceDirective() {
        val rubric = RubricInput(title = "General Rubric", text = "Criteria definitions...")
        val prompt = PromptBuilder.buildSystemPrompt(rubric)

        assertTrue(
            prompt.contains("Dynamically infer all sections, questions, and weights directly from the rubric text"),
            "System prompt must instruct model to dynamically infer sections, questions, and weights",
        )
        assertTrue(
            prompt.contains("Do not assume or enforce any hardcoded question list"),
            "System prompt must instruct model not to assume hardcoded questions",
        )
    }

    @Test
    fun buildSystemPrompt_schemaContainsAllExactDomainModelFields() {
        val rubric = RubricInput(title = "Schema Rubric", text = "Some rubric text")
        val prompt = PromptBuilder.buildSystemPrompt(rubric)

        val expectedFields =
            listOf(
                "subjectName",
                "overallSummary",
                "sections",
                "name",
                "questions",
                "id",
                "label",
                "weight",
                "rawScore",
                "comment",
                "sourceUrl",
            )

        for (field in expectedFields) {
            assertTrue(
                prompt.contains("\"$field\""),
                "Envelope schema in system prompt must contain domain field: \"$field\"",
            )
        }

        val excludedFields = listOf("weightedScore", "maxPoints", "grandTotal", "grandMax")
        for (field in excludedFields) {
            assertFalse(
                prompt.contains("\"$field\""),
                "Envelope schema in system prompt must not contain derived arithmetic field: \"$field\"",
            )
        }
    }

    @Test
    fun buildSystemPrompt_includesStandardTenPointRawScoreDirective() {
        val rubric = RubricInput(title = "Scoring Scale Rubric", text = "Criteria definitions...")
        val prompt = PromptBuilder.buildSystemPrompt(rubric)

        assertTrue(
            prompt.contains("Score each question with a rawScore from 0.0 to 10.0"),
            "System prompt must instruct model to use standard 0.0 to 10.0 scale for rawScore",
        )
    }

    @Test
    fun buildSystemPrompt_enforcesStrictRawJsonDirectiveWithoutMarkdownFencesOrPreamble() {
        val rubric = RubricInput(title = "Strict JSON Rubric", text = "Rubric body")
        val prompt = PromptBuilder.buildSystemPrompt(rubric)

        assertTrue(
            prompt.contains("Return ONLY a single, valid JSON object strictly matching the schema"),
            "Must instruct LLM to return only valid JSON object",
        )
        assertTrue(
            prompt.contains("Do NOT wrap the JSON in markdown code blocks (NEVER use ```json or ```)"),
            "Must forbid markdown code fences",
        )
        assertTrue(
            prompt.contains("Do NOT write any conversational preamble, introduction, or closing remarks"),
            "Must forbid conversational preambles",
        )
        assertTrue(
            prompt.contains("Your entire response MUST start with '{' as the very first character and end with '}' as the last character"),
            "Must require output to start with '{' and end with '}'",
        )
    }

    @Test
    fun buildSystemPrompt_includesAmbiguityAndTickerCollisionPolicy() {
        val rubric = RubricInput(title = "Ambiguity Rubric", text = "Rubric criteria")
        val prompt = PromptBuilder.buildSystemPrompt(rubric)

        assertTrue(
            prompt.contains("<ambiguity_policy>"),
            "System prompt must delineate ambiguity policy with XML tag",
        )
        assertTrue(
            prompt.contains("AMBIGUOUS SUBJECT / TICKER COLLISION RULE"),
            "System prompt must explicitly mention ticker collision rule",
        )
        assertTrue(
            prompt.contains("Do NOT guess, assume, or arbitrarily select any entity"),
            "System prompt must forbid guessing ambiguous subjects",
        )
        assertTrue(
            prompt.contains("\"sections\": []"),
            "System prompt must require empty sections array for ambiguous subjects",
        )
        assertFalse(
            prompt.contains("\"grandTotal\": 0.0"),
            "Ambiguity policy must not require grandTotal in JSON",
        )
        assertFalse(
            prompt.contains("\"grandMax\": 0.0"),
            "Ambiguity policy must not require grandMax in JSON",
        )
        assertTrue(
            prompt.contains("overallSummary"),
            "System prompt must require explanation in overallSummary",
        )
    }

    @Test
    fun buildUserPrompt_constructsAnalysisRequestWithSubjectQueryInXmlTag() {
        val subjectQuery = "Stripe Inc. (Fintech payments company)"
        val userPrompt = PromptBuilder.buildUserPrompt(subjectQuery)

        assertTrue(
            userPrompt.contains("Please analyze and score the following subject according to the evaluation rubric"),
            "User prompt must include analysis request text",
        )
        assertTrue(
            userPrompt.contains("<subject_query>\n$subjectQuery\n</subject_query>"),
            "User prompt must wrap subjectQuery inside <subject_query> tag",
        )
    }

    @Test
    fun promptBuilder_pureFunctionsAreDeterministicAndStateless() {
        val rubric = RubricInput(title = "Determinism Rubric", text = "Fixed rubric text")
        val subject = "Acme Robotics"

        val systemPrompt1 = PromptBuilder.buildSystemPrompt(rubric)
        val systemPrompt2 = PromptBuilder.buildSystemPrompt(rubric)
        val userPrompt1 = PromptBuilder.buildUserPrompt(subject)
        val userPrompt2 = PromptBuilder.buildUserPrompt(subject)

        assertEquals(systemPrompt1, systemPrompt2, "buildSystemPrompt must be purely deterministic")
        assertEquals(userPrompt1, userPrompt2, "buildUserPrompt must be purely deterministic")
    }

    @Test
    fun promptBuilder_sanitizesInputsByTrimmingLeadingAndTrailingWhitespace() {
        val untrimmedRubric =
            RubricInput(
                title = "   Whitespace Title   ",
                text = "   Whitespace Rubric Text   ",
            )
        val untrimmedSubject = "   Untrimmed Subject Query   "

        val systemPrompt = PromptBuilder.buildSystemPrompt(untrimmedRubric)
        val userPrompt = PromptBuilder.buildUserPrompt(untrimmedSubject)

        assertTrue(
            systemPrompt.contains("<rubric_title>\nWhitespace Title\n</rubric_title>"),
            "Rubric title must be trimmed",
        )
        assertTrue(
            systemPrompt.contains("<rubric_text>\nWhitespace Rubric Text\n</rubric_text>"),
            "Rubric text must be trimmed",
        )
        assertTrue(
            userPrompt.contains("<subject_query>\nUntrimmed Subject Query\n</subject_query>"),
            "Subject query must be trimmed",
        )
    }

    @Test
    fun companionObject_delegatesToDefaultPromptBuilderConsistently() {
        val rubric = RubricInput(title = "Delegation Test", text = "Delegation Rubric Text")
        val subject = "Target Subject"

        assertEquals(
            DefaultPromptBuilder.buildSystemPrompt(rubric),
            PromptBuilder.buildSystemPrompt(rubric),
            "Companion object delegation must match DefaultPromptBuilder for system prompt",
        )
        assertEquals(
            DefaultPromptBuilder.buildUserPrompt(subject),
            PromptBuilder.buildUserPrompt(subject),
            "Companion object delegation must match DefaultPromptBuilder for user prompt",
        )
    }

    @Test
    fun promptBuilder_supportsCustomImplementationForMocking() {
        val mockBuilder =
            object : PromptBuilder {
                override fun buildSystemPrompt(rubric: RubricInput): String = "mock-system-prompt"

                override fun buildUserPrompt(subjectQuery: String): String = "mock-user-prompt"
            }

        val dummyRubric = RubricInput(title = "Test", text = "Test")
        assertEquals("mock-system-prompt", mockBuilder.buildSystemPrompt(dummyRubric))
        assertEquals("mock-user-prompt", mockBuilder.buildUserPrompt("Test Subject"))
    }

    @Test
    fun buildSystemPrompt_omitsRubricTitleTagWhenTitleIsEmptyOrWhitespace() {
        val emptyTitleRubric = RubricInput(title = "", text = "Rubric without title")
        val blankTitleRubric = RubricInput(title = "   ", text = "Rubric with blank title")

        val emptyPrompt = PromptBuilder.buildSystemPrompt(emptyTitleRubric)
        val blankPrompt = PromptBuilder.buildSystemPrompt(blankTitleRubric)

        assertFalse(emptyPrompt.contains("<rubric_title>"), "Must omit <rubric_title> when title is empty")
        assertFalse(emptyPrompt.contains("</rubric_title>"), "Must omit </rubric_title> when title is empty")
        assertFalse(blankPrompt.contains("<rubric_title>"), "Must omit <rubric_title> when title is whitespace")
        assertFalse(blankPrompt.contains("</rubric_title>"), "Must omit </rubric_title> when title is whitespace")
        assertTrue(emptyPrompt.contains("<rubric_text>\nRubric without title\n</rubric_text>"))
    }

    @Test
    fun buildSystemPrompt_doesNotIncludeArithmeticConsistencyInstructions() {
        val rubric = RubricInput(title = "Math Rubric", text = "Rubric details")
        val prompt = PromptBuilder.buildSystemPrompt(rubric)

        assertFalse(
            prompt.contains(
                "Ensure weightedScore equals rawScore multiplied by weight for each question, and grandTotal equals the sum of all weighted scores.",
            ),
            "System prompt must not instruct model to calculate weightedScore and grandTotal",
        )
        assertFalse(
            prompt.contains("Ensure weightedScore equals rawScore"),
            "System prompt must not contain weightedScore arithmetic instructions",
        )
    }
}
