package com.tekome.vcman.presentation

import com.tekome.vcman.data.AnalysisError
import com.tekome.vcman.data.AnalysisException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalysisMessagesTest {
    @Test
    fun blankFieldsMessage_allFilledReturnsNull() {
        val fields =
            listOf(
                RequiredField("Rubric title", "Title"),
                RequiredField("API key", "sk-secret"),
            )

        assertNull(blankFieldsMessage(fields))
    }

    @Test
    fun blankFieldsMessage_whitespaceOnlyFieldsListedByLabelWithoutValues() {
        val fields =
            listOf(
                RequiredField("Rubric title", "T"),
                RequiredField("Subject", "   "),
                RequiredField("API key", "\t"),
            )

        val message = blankFieldsMessage(fields)

        assertEquals("Please fill in: Subject, API key.", message)
    }

    @Test
    fun blankFieldsMessage_emptyStringFieldIsListed() {
        val message = blankFieldsMessage(listOf(RequiredField("Subject", "")))

        assertEquals("Please fill in: Subject.", message)
    }

    @Test
    fun requiredField_toStringDoesNotExposeValue() {
        val field = RequiredField("API key", "sk-super-secret")

        assertFalse("sk-super-secret" in field.toString())
        assertTrue("API key" in field.toString())
    }

    @Test
    fun userMessageFor_networkReturnsConnectionMessage() {
        val message = userMessageFor(AnalysisError.Network)

        assertEquals("Network problem. Check your connection and try again.", message)
    }

    @Test
    fun userMessageFor_apiErrorWithStatusIncludesStatus() {
        val message = userMessageFor(AnalysisError.ApiError(httpStatus = 401))

        assertEquals("The AI service rejected the request (HTTP 401).", message)
    }

    @Test
    fun userMessageFor_apiErrorWithoutStatusOmitsStatus() {
        val message = userMessageFor(AnalysisError.ApiError(httpStatus = null))

        assertEquals("The AI service rejected the request.", message)
    }

    @Test
    fun userMessageFor_invalidResponseDoesNotExposeReason() {
        val message = userMessageFor(AnalysisError.InvalidResponse(reason = "internal-parser-detail"))

        assertFalse("internal-parser-detail" in message)
    }

    @Test
    fun userMessageFor_ambiguousSubjectIncludesExplanation() {
        val message = userMessageFor(AnalysisError.AmbiguousSubject(explanation = "2 companies named Acme"))

        assertTrue("2 companies named Acme" in message)
    }

    @Test
    fun userMessageFor_analysisExceptionDelegatesToErrorMapping() {
        val message = userMessageFor(AnalysisException(AnalysisError.Network))

        assertEquals(userMessageFor(AnalysisError.Network), message)
    }

    @Test
    fun userMessageFor_nonAnalysisExceptionReturnsFallbackWithoutRawMessage() {
        val message = userMessageFor(IllegalStateException("apiKey=sk-123 boom"))

        assertEquals(UNEXPECTED_ERROR_MESSAGE, message)
    }
}
