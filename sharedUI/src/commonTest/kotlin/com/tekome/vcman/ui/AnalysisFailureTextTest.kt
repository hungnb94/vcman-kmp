package com.tekome.vcman.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tekome.vcman.presentation.AnalysisFailure
import com.tekome.vcman.presentation.RequiredFieldId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Assertions here are locale independent: they check dynamic parameters and non-emptiness only. */
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

    @Test
    fun ambiguousSubject_includesExplanation() {
        assertTrue("2 companies" in textOf(AnalysisFailure.AmbiguousSubject("2 companies")))
    }

    @Test
    fun missingFields_isNotEmptyAndHasNoRawResourceKey() {
        val text = textOf(AnalysisFailure.MissingFields(listOf(RequiredFieldId.Subject, RequiredFieldId.ApiKey)))

        assertTrue(text.isNotBlank())
        assertFalse("setup_" in text)
    }

    @Test
    fun everyFailureHasText() {
        val all =
            listOf(
                AnalysisFailure.MissingFields(RequiredFieldId.entries),
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
