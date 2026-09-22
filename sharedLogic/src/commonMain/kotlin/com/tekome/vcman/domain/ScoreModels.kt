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
    val comment: String,
    val sourceUrl: String? = null,
) {
    val weightedScore: Double
        get() = rawScore * weight
}

data class ScoreSectionResult(
    val name: String,
    val questions: List<QuestionScoreResult>,
) {
    val total: Double
        get() = questions.sumOf { it.weightedScore }

    val maxPoints: Double
        get() = questions.sumOf { it.weight * MAX_RAW_SCORE }

    companion object {
        const val MAX_RAW_SCORE: Double = 10.0
    }
}

data class ProjectScoreReport(
    val subjectName: String,
    val rubricTitle: String,
    val overallSummary: String,
    val sections: List<ScoreSectionResult>,
    val generatedAtEpochMillis: Long,
) {
    val grandTotal: Double
        get() = sections.sumOf { it.total }

    val grandMax: Double
        get() = sections.sumOf { it.maxPoints }
}
