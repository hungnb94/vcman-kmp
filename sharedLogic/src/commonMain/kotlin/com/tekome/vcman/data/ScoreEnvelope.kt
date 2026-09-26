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
    }

@Serializable
internal data class EnvelopeDto(
    val subjectName: String,
    val overallSummary: String,
    val sections: List<SectionDto>,
)

@Serializable
internal data class SectionDto(
    val name: String,
    val questions: List<QuestionDto>,
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

internal fun decodeScoreReport(
    raw: String,
    rubricTitle: String,
    generatedAtEpochMillis: Long,
): ProjectScoreReport {
    val dto = decodeEnvelope(raw)

    if (dto.sections.isEmpty()) {
        throw AnalysisException(AnalysisError.AmbiguousSubject(dto.overallSummary))
    }

    val outOfRange =
        dto.sections
            .flatMap { it.questions }
            .firstOrNull { it.weight < 0.0 || it.rawScore !in 0.0..QuestionScoreResult.MAX_RAW_SCORE }
    if (outOfRange != null) {
        throw AnalysisException(AnalysisError.InvalidResponse("Question '${outOfRange.id}' has an out-of-range weight or rawScore"))
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

private fun decodeEnvelope(raw: String): EnvelopeDto {
    val candidates = findBalancedBraceCandidates(raw).sortedByDescending { it.length }.toList()
    if (candidates.isEmpty()) {
        throw AnalysisException(AnalysisError.InvalidResponse("LLM response does not contain a JSON object"))
    }

    var firstFailure: SerializationException? = null
    for (candidate in candidates) {
        try {
            return envelopeJson.decodeFromString(EnvelopeDto.serializer(), candidate)
        } catch (e: SerializationException) {
            if (firstFailure == null) firstFailure = e
        }
    }
    throw AnalysisException(AnalysisError.InvalidResponse("LLM response is not valid JSON"), firstFailure)
}

private fun findBalancedBraceCandidates(text: String): Sequence<String> =
    text.indices
        .asSequence()
        .filter { text[it] == '{' }
        .mapNotNull { start -> balancedEnd(text, start)?.let { end -> text.substring(start, end) } }

private fun balancedEnd(
    text: String,
    start: Int,
): Int? {
    var depth = 0
    var inString = false
    var i = start
    while (i < text.length) {
        val c = text[i]
        if (inString) {
            when (c) {
                '\\' -> i++
                '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> if (--depth == 0) return i + 1
            }
        }
        i++
    }
    return null
}
