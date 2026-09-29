package com.tekome.vcman.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.AnnotatedString
import com.tekome.vcman.data.SampleRubric
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test-only host that owns [SetupInput] with plain `remember`, mirroring how `AppContent` hoists it
 * in production. Lets [SetupScreen] itself stay fully stateless while keeping this test file's body
 * (call sites, assertions) unchanged from before the AC #11 refactor.
 */
@Composable
private fun StatefulSetupScreen(
    loading: Boolean,
    error: String?,
    onAnalyze: (rubricTitle: String, rubricText: String, subject: String, apiKey: String) -> Unit,
    sampleRubric: SampleRubric,
    initialInput: SetupInput = SetupInput(),
    onInputChange: (SetupInput) -> Unit = {},
) {
    var input by remember { mutableStateOf(initialInput) }
    SetupScreen(
        input = input,
        onInputChange = {
            input = it
            onInputChange(it)
        },
        loading = loading,
        error = error,
        onAnalyze = onAnalyze,
        sampleRubric = sampleRubric,
    )
}

@OptIn(ExperimentalTestApi::class)
class SetupScreenTest : ComposeUiTestRunner() {
    private val fakeSample = SampleRubric(title = "Demo", text = "A\nB")

    @Test
    fun analyze_forwardsCurrentValuesExactlyOnce() =
        runComposeUiTest {
            val calls = mutableListOf<List<String>>()
            setContent {
                StatefulSetupScreen(
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
            val calls = mutableListOf<List<String>>()
            setContent {
                StatefulSetupScreen(
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
            val calls = mutableListOf<List<String>>()
            setContent {
                StatefulSetupScreen(
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
                StatefulSetupScreen(
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
            val calls = mutableListOf<List<String>>()
            setContent {
                StatefulSetupScreen(
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
                StatefulSetupScreen(
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
                StatefulSetupScreen(
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
                StatefulSetupScreen(
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
                StatefulSetupScreen(
                    loading = false,
                    error = null,
                    onAnalyze = { _, _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.ERROR).assertDoesNotExist()
        }

    @Test
    fun input_fromOutside_isRenderedInNonSecretFields() =
        runComposeUiTest {
            setContent {
                SetupScreen(
                    input = SetupInput(subject = "Bitcoin"),
                    onInputChange = {},
                    loading = false,
                    error = null,
                    onAnalyze = { _, _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.SUBJECT)
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.EditableText,
                        AnnotatedString("Bitcoin"),
                    ),
                )
        }

    @Test
    fun typing_callsOnInputChange_withCopyOfChangedFieldOnly() =
        runComposeUiTest {
            val changes = mutableListOf<SetupInput>()
            setContent {
                SetupScreen(
                    input = SetupInput(subject = "Bitcoin", apiKey = "sk-123"),
                    onInputChange = { changes += it },
                    loading = false,
                    error = null,
                    onAnalyze = { _, _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.RUBRIC_TITLE).performTextInput("T")

            assertEquals(1, changes.size)
            assertEquals(SetupInput(rubricTitle = "T", subject = "Bitcoin", apiKey = "sk-123"), changes.single())
        }

    @Test
    fun loadSample_callsOnInputChange_onceKeepingSubjectAndApiKey() =
        runComposeUiTest {
            val changes = mutableListOf<SetupInput>()
            setContent {
                SetupScreen(
                    input = SetupInput(subject = "Bitcoin", apiKey = "sk-123"),
                    onInputChange = { changes += it },
                    loading = false,
                    error = null,
                    onAnalyze = { _, _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.LOAD_SAMPLE).performClick()

            assertEquals(1, changes.size)
            assertEquals(
                SetupInput(rubricTitle = fakeSample.title, rubricText = fakeSample.text, subject = "Bitcoin", apiKey = "sk-123"),
                changes.single(),
            )
        }
}

class SetupInputTest {
    @Test
    fun toString_neverExposesApiKey() {
        val input = SetupInput(rubricTitle = "a", rubricText = "b", subject = "c", apiKey = "sk-secret")

        val text = input.toString()

        assertTrue(!text.contains("sk-secret"))
        assertTrue(text.contains("a"))
        assertTrue(text.contains("b"))
        assertTrue(text.contains("c"))
    }

    @Test
    fun defaultConstructor_hasAllFieldsBlank() {
        assertEquals(SetupInput("", "", "", ""), SetupInput())
    }
}
