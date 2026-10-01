package com.tekome.vcman.presentation

import com.tekome.vcman.data.AnalysisError
import com.tekome.vcman.data.AnalysisException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalysisFailureTest {
    @Test
    fun missingFieldsFailure_allFilledReturnsNull() {
        val fields =
            listOf(
                RequiredField(RequiredFieldId.RubricTitle, "Title"),
                RequiredField(RequiredFieldId.ApiKey, "sk-secret"),
            )

        assertNull(missingFieldsFailure(fields))
    }

    @Test
    fun missingFieldsFailure_blankFieldsListedInTableOrderAsIds() {
        val fields =
            listOf(
                RequiredField(RequiredFieldId.RubricTitle, "T"),
                RequiredField(RequiredFieldId.Subject, "   "),
                RequiredField(RequiredFieldId.ApiKey, "\t"),
            )

        val failure = missingFieldsFailure(fields)

        assertEquals(
            AnalysisFailure.MissingFields(listOf(RequiredFieldId.Subject, RequiredFieldId.ApiKey)),
            failure,
        )
    }

    @Test
    fun missingFieldsFailure_emptyStringFieldIsListed() {
        val failure = missingFieldsFailure(listOf(RequiredField(RequiredFieldId.Subject, "")))

        assertEquals(AnalysisFailure.MissingFields(listOf(RequiredFieldId.Subject)), failure)
    }

    @Test
    fun requiredField_toStringDoesNotExposeValue() {
        val field = RequiredField(RequiredFieldId.ApiKey, "sk-super-secret")

        assertFalse("sk-super-secret" in field.toString())
        assertTrue("ApiKey" in field.toString())
    }

    @Test
    fun toFailure_mapsEveryAnalysisError() {
        val cases: List<Pair<AnalysisError, AnalysisFailure>> =
            listOf(
                AnalysisError.Network to AnalysisFailure.Network,
                AnalysisError.ApiError(httpStatus = 401) to AnalysisFailure.Api(401),
                AnalysisError.ApiError(httpStatus = null) to AnalysisFailure.Api(null),
                AnalysisError.InvalidResponse(reason = "internal-parser-detail") to AnalysisFailure.InvalidResponse,
                AnalysisError.AmbiguousSubject(explanation = "2 companies named Acme") to
                    AnalysisFailure.AmbiguousSubject("2 companies named Acme"),
            )

        cases.forEach { (error, expected) ->
            assertEquals(expected, error.toFailure(), "mapping for $error")
        }
    }

    @Test
    fun toFailure_analysisExceptionDelegatesToErrorMapping() {
        assertEquals(AnalysisFailure.Network, AnalysisException(AnalysisError.Network).toFailure())
    }

    @Test
    fun toFailure_nonAnalysisExceptionIsUnexpected() {
        val failure = IllegalStateException("apiKey=sk-123 boom").toFailure()

        assertEquals(AnalysisFailure.Unexpected, failure)
        assertFalse("sk-123" in failure.toString())
    }
}
