package com.tekome.vcman.ui

import androidx.compose.runtime.Composable
import com.tekome.vcman.presentation.AnalysisFailure
import com.tekome.vcman.presentation.RequiredFieldId
import org.jetbrains.compose.resources.stringResource
import vcman.sharedui.generated.resources.Res
import vcman.sharedui.generated.resources.error_ambiguous_subject
import vcman.sharedui.generated.resources.error_api
import vcman.sharedui.generated.resources.error_api_status
import vcman.sharedui.generated.resources.error_invalid_response
import vcman.sharedui.generated.resources.error_missing_fields
import vcman.sharedui.generated.resources.error_network
import vcman.sharedui.generated.resources.error_unexpected
import vcman.sharedui.generated.resources.setup_api_key_label
import vcman.sharedui.generated.resources.setup_rubric_text_label
import vcman.sharedui.generated.resources.setup_rubric_title_label
import vcman.sharedui.generated.resources.setup_subject_label

/**
 * Localized, user-facing text for this failure. Two axes of extension:
 * - closed set (this `when`): exhaustive with no `else`, so a new [AnalysisFailure] subtype will
 *   not build until it is given a message here;
 * - open set (languages): the strings live in `composeResources/values*`, so a new language only
 *   adds a resource file. Dynamic values (HTTP status, explanation, field names) are
 *   positional placeholders in those files, never string concatenation.
 */
@Composable
internal fun AnalysisFailure.asText(): String =
    when (this) {
        is AnalysisFailure.MissingFields ->
            stringResource(Res.string.error_missing_fields, fields.map { it.label() }.joinToString(", "))

        AnalysisFailure.Network -> stringResource(Res.string.error_network)

        is AnalysisFailure.Api ->
            httpStatus?.let { stringResource(Res.string.error_api_status, it) }
                ?: stringResource(Res.string.error_api)

        AnalysisFailure.InvalidResponse -> stringResource(Res.string.error_invalid_response)

        is AnalysisFailure.AmbiguousSubject -> stringResource(Res.string.error_ambiguous_subject, explanation)

        AnalysisFailure.Unexpected -> stringResource(Res.string.error_unexpected)
    }

/** Localized field name; reuses the setup-screen labels so each term has a single source. */
@Composable
internal fun RequiredFieldId.label(): String =
    stringResource(
        when (this) {
            RequiredFieldId.RubricTitle -> Res.string.setup_rubric_title_label
            RequiredFieldId.RubricText -> Res.string.setup_rubric_text_label
            RequiredFieldId.Subject -> Res.string.setup_subject_label
            RequiredFieldId.ApiKey -> Res.string.setup_api_key_label
        },
    )
