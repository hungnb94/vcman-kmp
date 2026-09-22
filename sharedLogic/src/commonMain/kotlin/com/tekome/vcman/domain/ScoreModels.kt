package com.tekome.vcman.domain

data class RubricInput(
    val title: String,
    val text: String,
)

data class QuestionScoreResult(
    val id: String,
    val label: String,
    val weight: Double,
    val rawScore: Double,
    val weightedScore: Double,
    val comment: String,
    val sourceUrl: String? = null,
)

data class ScoreSectionResult(
    val name: String,
    val maxPoints: Double,
    val questions: List<QuestionScoreResult>,
) {
    val total: Double
        get() = questions.sumOf { it.weightedScore }
}

data class ProjectScoreReport(
    val subjectName: String,
    val rubricTitle: String,
    val overallSummary: String,
    val sections: List<ScoreSectionResult>,
    val grandTotal: Double,
    val grandMax: Double,
    val generatedAtEpochMillis: Long,
)
