package com.tekome.vcman.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.tekome.vcman.data.SampleRubric
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SetupScreenTest : ComposeUiTestRunner() {
    private val fakeSample = SampleRubric(title = "Demo", text = "A\nB")

    private fun recordedAnalyzeCalls(): MutableList<List<String>> = mutableListOf()

    @Test
    fun analyze_forwardsCurrentValuesExactlyOnce() =
        runComposeUiTest {
            val calls = recordedAnalyzeCalls()
            setContent {
                SetupScreen(
                    loading = false,
                    error = null,
                    onAnalyze = { title, text, subject, apiKey -> calls += listOf(title, text, subject, apiKey) },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.RUBRIC_TITLE).performTextInput("T")
            onNodeWithTag(SetupScreenTags.RUBRIC_TEXT).performTextInput("line1\nline2")
            onNodeWithTag(SetupScreenTags.SUBJECT).performTextInput(" Bitcoin ")
            onNodeWithTag(SetupScreenTags.API_KEY).performTextInput("sk-123")
            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            assertEquals(listOf(listOf("T", "line1\nline2", " Bitcoin ", "sk-123")), calls)
        }

    @Test
    fun analyze_withEmptyFields_stillForwards() =
        runComposeUiTest {
            val calls = recordedAnalyzeCalls()
            setContent {
                SetupScreen(
                    loading = false,
                    error = null,
                    onAnalyze = { title, text, subject, apiKey -> calls += listOf(title, text, subject, apiKey) },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            assertEquals(listOf(listOf("", "", "", "")), calls)
        }

    @Test
    fun loadSample_fillsTitleAndText_andStaysEditable() =
        runComposeUiTest {
            val calls = recordedAnalyzeCalls()
            setContent {
                SetupScreen(
                    loading = false,
                    error = null,
                    onAnalyze = { title, text, subject, apiKey -> calls += listOf(title, text, subject, apiKey) },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.LOAD_SAMPLE).performClick()
            onNodeWithTag(SetupScreenTags.RUBRIC_TEXT).performTextInput("C")
            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            val (title, text) = calls.single().let { it[0] to it[1] }
            assertEquals("Demo", title)
            assertTrue(text.contains("A\nB"))
            assertTrue(text.contains("C"))
        }

    @Test
    fun apiKeyField_isMasked() =
        runComposeUiTest {
            setContent {
                SetupScreen(
                    loading = false,
                    error = null,
                    onAnalyze = { _, _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.API_KEY)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        }

    @Test
    fun loading_disablesAnalyze_andShowsIndicator() =
        runComposeUiTest {
            val calls = recordedAnalyzeCalls()
            setContent {
                SetupScreen(
                    loading = true,
                    error = null,
                    onAnalyze = { title, text, subject, apiKey -> calls += listOf(title, text, subject, apiKey) },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.ANALYZE).assertIsNotEnabled()
            onNodeWithTag(SetupScreenTags.LOADING).assertExists()
            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()

            assertTrue(calls.isEmpty())
        }

    @Test
    fun notLoading_hidesIndicator_andEnablesAnalyze() =
        runComposeUiTest {
            setContent {
                SetupScreen(
                    loading = false,
                    error = null,
                    onAnalyze = { _, _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.ANALYZE).assertIsEnabled()
            onNodeWithTag(SetupScreenTags.LOADING).assertDoesNotExist()
        }

    @Test
    fun error_isDisplayed() =
        runComposeUiTest {
            setContent {
                SetupScreen(
                    loading = false,
                    error = "Subject is required",
                    onAnalyze = { _, _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.ERROR).assertTextEquals("Subject is required")
        }

    @Test
    fun errorCleared_removesMessage() =
        runComposeUiTest {
            var currentError by mutableStateOf<String?>("Boom")
            setContent {
                SetupScreen(
                    loading = false,
                    error = currentError,
                    onAnalyze = { _, _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }
            onNodeWithTag(SetupScreenTags.ERROR).assertExists()

            currentError = null
            waitForIdle()

            onNodeWithTag(SetupScreenTags.ERROR).assertDoesNotExist()
        }

    @Test
    fun noError_hidesErrorMessageFromTheStart() =
        runComposeUiTest {
            setContent {
                SetupScreen(
                    loading = false,
                    error = null,
                    onAnalyze = { _, _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.ERROR).assertDoesNotExist()
        }
}
