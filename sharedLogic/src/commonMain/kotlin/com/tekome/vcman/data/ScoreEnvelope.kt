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

private val CODE_FENCE_REGEX = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE)

private fun findBalancedBraceCandidates(text: String): List<String> {
    val candidates = mutableListOf<String>()
    var i = 0
    while (i < text.length) {
        if (text[i] == '{') {
            val start = i
            var depth = 1
            var inString = false
            i++
            while (i < text.length && depth > 0) {
                val c = text[i]
                if (inString) {
                    if (c == '\\') {
                        i += 2
                        continue
                    } else if (c == '"') {
                        inString = false
                    }
                } else {
                    when (c) {
                        '"' -> inString = true
                        '{' -> depth++
                        '}' -> depth--
                    }
                }
                i++
            }
            if (depth == 0) {
                candidates.add(text.substring(start, i))
            }
        } else {
            i++
        }
    }
    return candidates
}

/**
 * Extracts the outermost JSON object candidate from [raw].
 * Handles Markdown code fences (e.g. ```json ... ```), preambles, and postscripts that may contain
 * curly braces, while respecting escaped quotes and braces inside JSON strings.
 */
private fun extractJsonObject(raw: String): String {
    val fencedCandidates =
        CODE_FENCE_REGEX
            .findAll(raw)
            .flatMap { match -> findBalancedBraceCandidates(match.groupValues[1]) }
            .toList()

    val candidates =
        fencedCandidates.ifEmpty {
            findBalancedBraceCandidates(raw)
        }

    val best =
        candidates
            .filter { it.contains(':') || it.trim() == "{}" }
            .maxByOrNull { it.length }
            ?: candidates.maxByOrNull { it.length }

    requireNotNull(best) { "No JSON object found in LLM response" }
    return best.trim()
}
