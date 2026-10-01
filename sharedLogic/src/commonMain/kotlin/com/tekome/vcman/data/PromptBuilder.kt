package com.tekome.vcman.data

import com.tekome.vcman.domain.LanguageTag
import com.tekome.vcman.domain.RubricInput

interface PromptBuilder {
    fun buildSystemPrompt(rubric: RubricInput): String

    fun buildUserPrompt(
        subjectQuery: String,
        outputLanguage: LanguageTag,
    ): String

    companion object : PromptBuilder by DefaultPromptBuilder
}

internal object DefaultPromptBuilder : PromptBuilder {
    override fun buildSystemPrompt(rubric: RubricInput): String {
        val cleanTitle = rubric.title.trim()
        val cleanText = rubric.text.trim()
        return listOfNotNull(
            BASE_SYSTEM_INSTRUCTIONS,
            if (cleanTitle.isNotEmpty()) "<rubric_title>\n$cleanTitle\n</rubric_title>" else null,
            "<rubric_text>\n$cleanText\n</rubric_text>",
            AMBIGUITY_POLICY,
            RAW_JSON_DIRECTIVE,
            ENVELOPE_SCHEMA,
        ).joinToString("\n\n")
    }

    override fun buildUserPrompt(
        subjectQuery: String,
        outputLanguage: LanguageTag,
    ): String {
        val cleanQuery = subjectQuery.trim()
        return listOf(
            "Please analyze and score the following subject according to the evaluation rubric.",
            "<subject_query>\n$cleanQuery\n</subject_query>",
            "<output_language>\n${outputLanguageDirective(outputLanguage)}\n</output_language>",
        ).joinToString("\n\n")
    }
}

/**
 * One shared instruction for every language: the tag is data, so adding a language never touches
 * this function. JSON keys, enum values and numbers stay as defined by the schema so parsing
 * does not depend on the answer language.
 */
private fun outputLanguageDirective(language: LanguageTag): String =
    "Write all human-readable text values (subjectName, overallSummary, section names, question labels, " +
        "comments) in the language with BCP-47 tag \"${language.value}\". " +
        "Keep every JSON key, enum value and number exactly as defined by the schema; do not translate or localize them."

private const val BASE_SYSTEM_INSTRUCTIONS: String = """You are an expert venture capital analyst evaluating investment opportunities.
Analyze the target subject strictly according to the evaluation rubric provided below.
Dynamically infer all sections, questions, and weights directly from the rubric text.
Do not assume or enforce any hardcoded question list.
Score each question with a rawScore from 0.0 to 10.0 (where 0.0 is completely inadequate and 10.0 is exceptional).
Provide a credible citation link in "sourceUrl" if evidence/data was referenced, or null if no direct URL is available."""

private const val AMBIGUITY_POLICY: String = """<ambiguity_policy>
AMBIGUOUS SUBJECT / TICKER COLLISION RULE:
If the subject query is ambiguous, vague, matches multiple distinct entities (e.g., duplicate stock tickers across exchanges, or multiple companies sharing the same name), or contains insufficient details to uniquely identify the target:
1. STRICT PROHIBITION: Do NOT guess, assume, or arbitrarily select any entity.
2. EMPTY SECTIONS: Return an empty array for sections: "sections": [].
3. EXPLANATION: In "overallSummary", explicitly state that the subject is ambiguous, list the conflicting candidates identified, and specify the information needed to disambiguate.
</ambiguity_policy>"""

private const val RAW_JSON_DIRECTIVE: String = """OUTPUT FORMAT REQUIREMENTS:
- Return ONLY a single, valid JSON object strictly matching the schema below.
- Do NOT wrap the JSON in markdown code blocks (NEVER use ```json or ```).
- Do NOT write any conversational preamble, introduction, or closing remarks.
- Your entire response MUST start with '{' as the very first character and end with '}' as the last character."""

private const val ENVELOPE_SCHEMA: String = """<envelope_schema>
{
  "subjectName": "string",
  "overallSummary": "string",
  "sections": [
    {
      "name": "string",
      "questions": [
        {
          "id": "string",
          "label": "string",
          "weight": 1.0,
          "rawScore": 0.0,
          "comment": "string",
          "sourceUrl": null
        }
      ]
    }
  ]
}
</envelope_schema>"""
