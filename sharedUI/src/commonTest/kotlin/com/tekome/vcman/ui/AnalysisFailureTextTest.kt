package com.tekome.vcman.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tekome.vcman.presentation.AnalysisFailure
import com.tekome.vcman.presentation.RequiredFieldId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AnalysisFailureTextTest : ComposeUiTestRunner() {
    private fun textOf(failure: AnalysisFailure): String {
        var result = ""
        runComposeUiTest {
            setContent { result = failure.asText() }
            waitForIdle()
        }
        return result
    }

    @Test
    fun api_includesHttpStatus() {
        assertTrue("401" in textOf(AnalysisFailure.Api(401)))
    }

    private fun noticeOf(failure: AnalysisFailure): SetupNotice {
        lateinit var result: SetupNotice
        runComposeUiTest {
            setContent { result = failure.asNotice() }
            waitForIdle()
        }
        return result
    }

    @Test
    fun asClarificationDetail_blankBecomesNull() {
        listOf("", "   ", "\n\t").forEach { assertNull(it.asClarificationDetail(), "blank: '$it'") }
    }

    @Test
    fun asClarificationDetail_keepsTrimmedText() {
        assertEquals("2 companies", "  2 companies \n".asClarificationDetail())
    }

    @Test
    fun asNotice_ambiguousIsClarificationWithDetail() {
        val notice = assertIs<SetupNotice.Clarification>(noticeOf(AnalysisFailure.AmbiguousSubject("2 companies")))

        assertEquals("2 companies", notice.detail)
        assertTrue(notice.title.isNotBlank())
        assertTrue(notice.hint.isNotBlank())
    }

    @Test
    fun asNotice_blankExplanationHasNullDetail() {
        val notice = assertIs<SetupNotice.Clarification>(noticeOf(AnalysisFailure.AmbiguousSubject("  ")))

        assertNull(notice.detail)
    }

    @Test
    fun asNotice_everyOtherFailureIsErrorWithAsText() {
        val others =
            listOf(
                AnalysisFailure.MissingFields(RequiredFieldId.entries),
                AnalysisFailure.NotConfigured,
                AnalysisFailure.Network,
                AnalysisFailure.Api(null),
                AnalysisFailure.Api(500),
                AnalysisFailure.InvalidResponse,
                AnalysisFailure.Unexpected,
            )

        others.forEach {
            assertEquals(SetupNotice.Error(textOf(it)), noticeOf(it), "notice for $it")
        }
    }

    @Test
    fun missingFields_isNotEmptyAndHasNoRawResourceKey() {
        val text = textOf(AnalysisFailure.MissingFields(listOf(RequiredFieldId.Subject, RequiredFieldId.RubricText)))

        assertTrue(text.isNotBlank())
        assertFalse("setup_" in text)
    }

    @Test
    fun everyFailureHasText() {
        val all =
            listOf(
                AnalysisFailure.MissingFields(RequiredFieldId.entries),
                AnalysisFailure.NotConfigured,
                AnalysisFailure.Network,
                AnalysisFailure.Api(null),
                AnalysisFailure.Api(500),
                AnalysisFailure.InvalidResponse,
                AnalysisFailure.AmbiguousSubject("x"),
                AnalysisFailure.Unexpected,
            )

        all.forEach { assertTrue(textOf(it).isNotBlank(), "text for $it") }
    }
}
