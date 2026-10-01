package com.tekome.vcman.presentation

import com.tekome.vcman.data.AnalysisError
import com.tekome.vcman.data.AnalysisException

enum class RequiredFieldId {
    RubricTitle,
    RubricText,
    Subject,
    ApiKey,
}

sealed interface AnalysisFailure {
    data class MissingFields(
        val fields: List<RequiredFieldId>,
    ) : AnalysisFailure

    data object Network : AnalysisFailure

    data class Api(
        val httpStatus: Int?,
    ) : AnalysisFailure

    data object InvalidResponse : AnalysisFailure

    data class AmbiguousSubject(
        val explanation: String,
    ) : AnalysisFailure

    data object Unexpected : AnalysisFailure
}

internal class RequiredField(
    val id: RequiredFieldId,
    val value: String,
) {
    override fun toString(): String = "RequiredField(id=$id)"
}

internal fun missingFieldsFailure(fields: List<RequiredField>): AnalysisFailure.MissingFields? {
    val missing = fields.filter { it.value.isBlank() }.map { it.id }
    return if (missing.isEmpty()) null else AnalysisFailure.MissingFields(missing)
}

internal fun Throwable.toFailure(): AnalysisFailure = (this as? AnalysisException)?.error?.toFailure() ?: AnalysisFailure.Unexpected

internal fun AnalysisError.toFailure(): AnalysisFailure =
    when (this) {
        AnalysisError.Network -> AnalysisFailure.Network
        is AnalysisError.ApiError -> AnalysisFailure.Api(httpStatus)
        is AnalysisError.InvalidResponse -> AnalysisFailure.InvalidResponse
        is AnalysisError.AmbiguousSubject -> AnalysisFailure.AmbiguousSubject(explanation)
    }
