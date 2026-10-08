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

@Composable
private fun StatefulSetupScreen(
    loading: Boolean,
    notice: SetupNotice?,
    onAnalyze: (rubricTitle: String, rubricText: String, subject: String) -> Unit,
    sampleRubric: SampleRubric,
    onOpenSettings: () -> Unit = {},
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
        notice = notice,
        onAnalyze = onAnalyze,
        onOpenSettings = onOpenSettings,
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
                    notice = null,
                    onAnalyze = { title, text, subject -> calls += listOf(title, text, subject) },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.RUBRIC_TITLE).performTextInput("T")
            onNodeWithTag(SetupScreenTags.RUBRIC_TEXT).performTextInput("line1\nline2")
            onNodeWithTag(SetupScreenTags.SUBJECT).performTextInput(" Bitcoin ")
            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            assertEquals(listOf(listOf("T", "line1\nline2", " Bitcoin ")), calls)
        }

    @Test
    fun analyze_withEmptyFields_stillForwards() =
        runComposeUiTest {
            val calls = mutableListOf<List<String>>()
            setContent {
                StatefulSetupScreen(
                    loading = false,
                    notice = null,
                    onAnalyze = { title, text, subject -> calls += listOf(title, text, subject) },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.ANALYZE).performScrollTo().performClick()
            waitForIdle()

            assertEquals(listOf(listOf("", "", "")), calls)
        }

    @Test
    fun loadSample_fillsTitleAndText_andStaysEditable() =
        runComposeUiTest {
            val calls = mutableListOf<List<String>>()
            setContent {
                StatefulSetupScreen(
                    loading = false,
                    notice = null,
                    onAnalyze = { title, text, subject -> calls += listOf(title, text, subject) },
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
    fun apiKeyField_isGone() =
        runComposeUiTest {
            setContent {
                StatefulSetupScreen(
                    loading = false,
                    notice = null,
                    onAnalyze = { _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag("setup_api_key").assertDoesNotExist()
        }

    @Test
    fun openSettings_invokesCallbackOnce() =
        runComposeUiTest {
            var opened = 0
            setContent {
                StatefulSetupScreen(
                    loading = false,
                    notice = null,
                    onAnalyze = { _, _, _ -> },
                    sampleRubric = fakeSample,
                    onOpenSettings = { opened++ },
                )
            }

            onNodeWithTag(SetupScreenTags.OPEN_SETTINGS).performClick()

            assertEquals(1, opened)
        }

    @Test
    fun openSettings_isAvailableWhileLoading() =
        runComposeUiTest {
            setContent {
                StatefulSetupScreen(
                    loading = true,
                    notice = null,
                    onAnalyze = { _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.OPEN_SETTINGS).assertIsEnabled()
        }

    @Test
    fun loading_disablesAnalyze_andShowsIndicator() =
        runComposeUiTest {
            val calls = mutableListOf<List<String>>()
            setContent {
                StatefulSetupScreen(
                    loading = true,
                    notice = null,
                    onAnalyze = { title, text, subject -> calls += listOf(title, text, subject) },
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
                    notice = null,
                    onAnalyze = { _, _, _ -> },
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
                    notice = SetupNotice.Error("Subject is required"),
                    onAnalyze = { _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.ERROR).assertTextEquals("Subject is required")
        }

    @Test
    fun errorCleared_removesMessage() =
        runComposeUiTest {
            var currentError by mutableStateOf<SetupNotice?>(SetupNotice.Error("Boom"))
            setContent {
                StatefulSetupScreen(
                    loading = false,
                    notice = currentError,
                    onAnalyze = { _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }
            onNodeWithTag(SetupScreenTags.ERROR).assertExists()

            currentError = null
            waitForIdle()

            onNodeWithTag(SetupScreenTags.ERROR).assertDoesNotExist()
        }

    @Test
    fun clarification_isDisplayedWithDetail_andIsNotAnError() =
        runComposeUiTest {
            setContent {
                StatefulSetupScreen(
                    loading = false,
                    notice = SetupNotice.Clarification(title = "Title", hint = "Hint", detail = "Candidates A, B"),
                    onAnalyze = { _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.CLARIFICATION).assertExists()
            onNodeWithTag(SetupScreenTags.CLARIFICATION_DETAIL, useUnmergedTree = true).assertTextEquals("Candidates A, B")
            onNodeWithTag(SetupScreenTags.ERROR).assertDoesNotExist()
        }

    @Test
    fun clarification_withoutDetail_hidesDetailNode() =
        runComposeUiTest {
            setContent {
                StatefulSetupScreen(
                    loading = false,
                    notice = SetupNotice.Clarification(title = "Title", hint = "Hint", detail = null),
                    onAnalyze = { _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.CLARIFICATION).assertExists()
            onNodeWithTag(SetupScreenTags.CLARIFICATION_DETAIL, useUnmergedTree = true).assertDoesNotExist()
        }

    @Test
    fun error_doesNotShowClarification() =
        runComposeUiTest {
            setContent {
                StatefulSetupScreen(
                    loading = false,
                    notice = SetupNotice.Error("Boom"),
                    onAnalyze = { _, _, _ -> },
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.CLARIFICATION).assertDoesNotExist()
        }

    @Test
    fun noError_hidesErrorMessageFromTheStart() =
        runComposeUiTest {
            setContent {
                StatefulSetupScreen(
                    loading = false,
                    notice = null,
                    onAnalyze = { _, _, _ -> },
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
                    notice = null,
                    onAnalyze = { _, _, _ -> },
                    onOpenSettings = {},
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
                    input = SetupInput(subject = "Bitcoin"),
                    onInputChange = { changes += it },
                    loading = false,
                    notice = null,
                    onAnalyze = { _, _, _ -> },
                    onOpenSettings = {},
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.RUBRIC_TITLE).performTextInput("T")

            assertEquals(1, changes.size)
            assertEquals(SetupInput(rubricTitle = "T", subject = "Bitcoin"), changes.single())
        }

    @Test
    fun loadSample_callsOnInputChange_onceKeepingSubject() =
        runComposeUiTest {
            val changes = mutableListOf<SetupInput>()
            setContent {
                SetupScreen(
                    input = SetupInput(subject = "Bitcoin"),
                    onInputChange = { changes += it },
                    loading = false,
                    notice = null,
                    onAnalyze = { _, _, _ -> },
                    onOpenSettings = {},
                    sampleRubric = fakeSample,
                )
            }

            onNodeWithTag(SetupScreenTags.LOAD_SAMPLE).performClick()

            assertEquals(1, changes.size)
            assertEquals(
                SetupInput(
                    rubricTitle = fakeSample.title,
                    rubricText = fakeSample.text,
                    subject = "Bitcoin",
                ),
                changes.single(),
            )
        }
}

class SetupInputTest {
    @Test
    fun defaultConstructor_hasAllFieldsBlank() {
        assertEquals(SetupInput("", "", ""), SetupInput())
    }
}
