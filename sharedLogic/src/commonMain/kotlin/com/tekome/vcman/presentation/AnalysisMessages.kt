package com.tekome.vcman.presentation

import com.tekome.vcman.data.AnalysisError
import com.tekome.vcman.data.AnalysisException

internal const val UNEXPECTED_ERROR_MESSAGE = "Something went wrong. Please try again."

/**
 * One row of the "required, non-blank" validation table: a display label paired with the raw
 * value to check. [value] is intentionally never surfaced via [toString] so secrets such as an
 * API key never leak into logs or error messages.
 */
internal class RequiredField(
    val label: String,
    val value: String,
) {
    override fun toString(): String = "RequiredField(label=$label)"
}

/**
 * Returns a message listing the blank fields (by label only), or `null` if every field is
 * non-blank. Adding/removing a required field only means adding/removing a [RequiredField] row
 * at the call site - this function never needs to change.
 */
internal fun blankFieldsMessage(fields: List<RequiredField>): String? {
    val missing = fields.filter { it.value.isBlank() }.map { it.label }
    return if (missing.isEmpty()) null else "Please fill in: ${missing.joinToString(", ")}."
}

/**
 * Open set (any [Throwable]): only a single generic fallback message, because classifying a
 * throwable into an [AnalysisError] is already the responsibility of the `data` layer
 * (`errorRules`/`classify`). If [throwable] has already been classified (carries an
 * [AnalysisException]), delegate to the closed-set overload below.
 */
internal fun userMessageFor(throwable: Throwable): String =
    (throwable as? AnalysisException)?.error?.let(::userMessageFor) ?: UNEXPECTED_ERROR_MESSAGE

/**
 * Closed set ([AnalysisError] is a sealed interface): exhaustive `when`, no `else`. Adding a new
 * [AnalysisError] branch will make this function fail to build right here until a matching
 * message is added - this is the compiler-enforced OCP mechanism for a closed set, not a rigid
 * if/else chain.
 */
internal fun userMessageFor(error: AnalysisError): String =
    when (error) {
        AnalysisError.Network ->
            "Network problem. Check your connection and try again."

        is AnalysisError.ApiError ->
            error.httpStatus?.let { "The AI service rejected the request (HTTP $it)." }
                ?: "The AI service rejected the request."

        is AnalysisError.InvalidResponse ->
            "The AI returned a response we could not read. Please try again."

        is AnalysisError.AmbiguousSubject ->
            "The subject is ambiguous: ${error.explanation}"
    }
