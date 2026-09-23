package com.tekome.vcman.data

import com.tekome.vcman.domain.ProjectScoreReport
import com.tekome.vcman.domain.QuestionScoreResult
import com.tekome.vcman.domain.ScoreSectionResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val envelopeJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

@Serializable
internal data class EnvelopeDto(
    val subjectName: String,
    val overallSummary: String,
    val sections: List<SectionDto> = emptyList(),
)

@Serializable
internal data class SectionDto(
    val name: String,
    val questions: List<QuestionDto> = emptyList(),
)

@Serializable
internal data class QuestionDto(
    val id: String,
    val label: String,
    val weight: Double,
    val rawScore: Double,
    val comment: String,
    val sourceUrl: String? = null,
)

/**
 * Extracts the JSON object embedded in [raw] (the LLM may still add a code fence or a
 * conversational preamble despite [PromptBuilder]'s instructions not to), decodes it against the
 * envelope schema, applies the ambiguity rule, and maps the result to the domain
 * [ProjectScoreReport].
 *
 * @throws AnalysisException with [AnalysisError.AmbiguousSubject] when the model reports an empty
 * `sections` array — a valid, schema-conforming response per `AMBIGUITY_POLICY`, not a decode
 * error.
 * @throws AnalysisException with [AnalysisError.InvalidResponse] when [raw] cannot be decoded into
 * the envelope schema.
 */
internal fun decodeScoreReport(
    raw: String,
    rubricTitle: String,
    generatedAtEpochMillis: Long,
): ProjectScoreReport {
    val dto =
        try {
            envelopeJson.decodeFromString(EnvelopeDto.serializer(), extractJsonObject(raw))
        } catch (e: SerializationException) {
            throw AnalysisException(AnalysisError.InvalidResponse("LLM response is not valid JSON"), e)
        } catch (e: IllegalArgumentException) {
            throw AnalysisException(AnalysisError.InvalidResponse("LLM response does not contain a JSON object"), e)
        }

    if (dto.sections.isEmpty()) {
        throw AnalysisException(AnalysisError.AmbiguousSubject(dto.overallSummary))
    }

    return ProjectScoreReport(
        subjectName = dto.subjectName,
        rubricTitle = rubricTitle,
        overallSummary = dto.overallSummary,
        sections =
            dto.sections.map { section ->
                ScoreSectionResult(
                    name = section.name,
                    questions =
                        section.questions.map { question ->
                            QuestionScoreResult(
                                id = question.id,
                                label = question.label,
                                weight = question.weight,
                                rawScore = question.rawScore,
                                comment = question.comment,
                                sourceUrl = question.sourceUrl,
                            )
                        },
                )
            },
        generatedAtEpochMillis = generatedAtEpochMillis,
    )
}

/** LLMs sometimes wrap the JSON object in a code fence or add prose around it despite the prompt. */
private fun extractJsonObject(raw: String): String {
    val trimmed = raw.trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    require(start in 0..end) { "No JSON object found in LLM response" }
    return trimmed.substring(start, end + 1)
}
