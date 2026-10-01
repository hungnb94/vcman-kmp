package com.tekome.vcman.presentation

import com.tekome.vcman.data.AnalysisError
import com.tekome.vcman.data.AnalysisException

/** Identifies a required input field. The UI layer owns the (localized) label for each id. */
enum class RequiredFieldId {
    RubricTitle,
    RubricText,
    Subject,
    ApiKey,
}

/**
 * Closed set of user-facing failures, described as data only: this layer decides *what* went
 * wrong, the UI layer decides *how to say it* in the current locale (string resources).
 * Adding a subtype makes the UI's exhaustive `when` fail to build until a message exists for it.
 */
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

/**
 * One row of the "required, non-blank" validation table: a field id paired with the raw value to
 * check. [value] is intentionally never surfaced via [toString] so secrets such as an API key
 * never leak into logs or error messages.
 */
internal class RequiredField(
    val id: RequiredFieldId,
    val value: String,
) {
    override fun toString(): String = "RequiredField(id=$id)"
}

/**
 * Returns [AnalysisFailure.MissingFields] listing the blank fields (ids only, in table order), or
 * `null` if every field is non-blank. Adding/removing a required field only means adding/removing
 * a [RequiredField] row at the call site - this function never needs to change.
 */
internal fun missingFieldsFailure(fields: List<RequiredField>): AnalysisFailure.MissingFields? {
    val missing = fields.filter { it.value.isBlank() }.map { it.id }
    return if (missing.isEmpty()) null else AnalysisFailure.MissingFields(missing)
}

/**
 * Open set (any [Throwable]): only a single generic fallback, because classifying a throwable
 * into an [AnalysisError] is already the responsibility of the `data` layer
 * (`errorRules`/`classify`). If [this] has already been classified (carries an
 * [AnalysisException]), delegate to the closed-set overload below.
 */
internal fun Throwable.toFailure(): AnalysisFailure =
    (this as? AnalysisException)?.error?.toFailure() ?: AnalysisFailure.Unexpected

/**
 * Closed set ([AnalysisError] is a sealed interface): exhaustive `when`, no `else`. Adding a new
 * [AnalysisError] branch will make this function fail to build right here until a matching
 * failure is mapped - this is the compiler-enforced OCP mechanism for a closed set, not a rigid
 * if/else chain. [AnalysisError.InvalidResponse.reason] is an internal detail and is dropped.
 */
internal fun AnalysisError.toFailure(): AnalysisFailure =
    when (this) {
        AnalysisError.Network -> AnalysisFailure.Network
        is AnalysisError.ApiError -> AnalysisFailure.Api(httpStatus)
        is AnalysisError.InvalidResponse -> AnalysisFailure.InvalidResponse
        is AnalysisError.AmbiguousSubject -> AnalysisFailure.AmbiguousSubject(explanation)
    }
