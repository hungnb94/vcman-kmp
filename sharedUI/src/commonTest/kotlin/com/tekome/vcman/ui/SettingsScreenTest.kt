package com.tekome.vcman.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tekome.vcman.data.ApiKey
import com.tekome.vcman.data.ConnectionTestResult
import com.tekome.vcman.data.LlmProviderType
import com.tekome.vcman.data.SettingsField
import com.tekome.vcman.data.SettingsFieldError
import com.tekome.vcman.presentation.ConnectionTestState
import com.tekome.vcman.presentation.SaveStatus
import com.tekome.vcman.presentation.SettingsForm
import com.tekome.vcman.presentation.SettingsUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Composable
private fun TestSettingsScreen(
    state: SettingsUiState,
    onSelectProvider: (LlmProviderType) -> Unit = {},
    onApiKeyChange: (String) -> Unit = {},
    onBaseUrlChange: (String) -> Unit = {},
    onModelChange: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onTestConnection: () -> Unit = {},
    onBack: () -> Unit = {},
) = SettingsScreen(
    state = state,
    onSelectProvider = onSelectProvider,
    onApiKeyChange = onApiKeyChange,
    onBaseUrlChange = onBaseUrlChange,
    onModelChange = onModelChange,
    onSave = onSave,
    onTestConnection = onTestConnection,
    onBack = onBack,
)

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest : ComposeUiTestRunner() {
    private val state = SettingsUiState(loaded = true)

    @Test
    fun rendersOneRadioPerProviderAndMarksTheSelectedOne() =
        runComposeUiTest {
            setContent { TestSettingsScreen(state.copy(form = SettingsForm.defaultsFor(LlmProviderType.AnthropicCompatible))) }

            val radios =
                onAllNodes(
                    SemanticsMatcher("is provider radio") {
                        SemanticsProperties.TestTag in it.config && it.config[SemanticsProperties.TestTag].startsWith(SettingsScreenTags.PROVIDER_PREFIX)
                    },
                )
            assertEquals(LlmProviderType.entries.size, radios.fetchSemanticsNodes().size)
            LlmProviderType.entries.forEach { type ->
                val node = onNodeWithTag(SettingsScreenTags.provider(type))
                if (type == LlmProviderType.AnthropicCompatible) node.assertIsSelected() else node.assertIsNotSelected()
            }
        }

    @Test
    fun selectingProviderReportsIt() =
        runComposeUiTest {
            val selected = mutableListOf<LlmProviderType>()
            setContent { TestSettingsScreen(state, onSelectProvider = { selected += it }) }

            LlmProviderType.entries.forEach { onNodeWithTag(SettingsScreenTags.provider(it)).performClick() }

            assertEquals(LlmProviderType.entries.toList(), selected)
        }

    @Test
    fun apiKeyIsMaskedByDefaultAndRevealedByToggle() =
        runComposeUiTest {
            setContent { TestSettingsScreen(state.copy(form = state.form.copy(apiKey = ApiKey("sk-1")))) }

            onNodeWithTag(SettingsScreenTags.API_KEY).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))

            onNodeWithTag(SettingsScreenTags.TOGGLE_KEY).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(SettingsScreenTags.API_KEY).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Password))

            onNodeWithTag(SettingsScreenTags.TOGGLE_KEY).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(SettingsScreenTags.API_KEY).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        }

    @Test
    fun showsFormValuesAndForwardsEdits() =
        runComposeUiTest {
            val edits = mutableListOf<String>()
            setContent {
                TestSettingsScreen(
                    state.copy(form = SettingsForm(LlmProviderType.OpenAICompatible, ApiKey("k"), "https://x.example.com", "m1")),
                    onApiKeyChange = { edits += "key:$it" },
                    onBaseUrlChange = { edits += "url:$it" },
                    onModelChange = { edits += "model:$it" },
                )
            }

            onNodeWithTag(SettingsScreenTags.BASE_URL)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("https://x.example.com")))
            onNodeWithTag(SettingsScreenTags.MODEL)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("m1")))

            onNodeWithTag(SettingsScreenTags.API_KEY).performTextReplacement("a")
            onNodeWithTag(SettingsScreenTags.BASE_URL).performTextReplacement("b")
            onNodeWithTag(SettingsScreenTags.MODEL).performTextReplacement("c")

            // Controlled fields also echo their current value back; only the typed replacements matter here.
            assertEquals(listOf("key:a", "url:b", "model:c"), edits.filter { it.substringAfter(":") in setOf("a", "b", "c") })
        }

    @Test
    fun noErrorsShownForCleanState() =
        runComposeUiTest {
            setContent { TestSettingsScreen(state) }

            SettingsFieldError.entries.forEach {
                onNodeWithTag(SettingsScreenTags.ERROR_PREFIX + it.name, useUnmergedTree = true).assertDoesNotExist()
            }
        }

    @Test
    fun everyFieldErrorIsShownUnderItsOwnField() =
        runComposeUiTest {
            var current by mutableStateOf(state)
            setContent { TestSettingsScreen(current) }
            SettingsFieldError.entries.forEach { error ->
                current = state.copy(errors = setOf(error))
                waitForIdle()
                onNodeWithTag(SettingsScreenTags.ERROR_PREFIX + error.name, useUnmergedTree = true).assertExists()
                SettingsFieldError.entries.filter { it != error }.forEach {
                    onNodeWithTag(SettingsScreenTags.ERROR_PREFIX + it.name, useUnmergedTree = true).assertDoesNotExist()
                }
            }
        }

    @Test
    fun errorsFromAllFieldsAreShownTogether() =
        runComposeUiTest {
            setContent { TestSettingsScreen(state.copy(errors = SettingsFieldError.entries.toSet())) }

            SettingsFieldError.entries.forEach {
                onNodeWithTag(SettingsScreenTags.ERROR_PREFIX + it.name, useUnmergedTree = true).assertExists()
            }
            assertEquals(SettingsField.entries.size, SettingsFieldError.entries.map { it.field }.toSet().size)
        }

    @Test
    fun insecureWarningOnlyWhenBaseUrlIsRemoteHttp() =
        runComposeUiTest {
            setContent { TestSettingsScreen(state.copy(form = state.form.copy(baseUrl = "http://192.168.1.10:11434"))) }
            onNodeWithTag(SettingsScreenTags.INSECURE_WARNING).assertExists()
        }

    @Test
    fun noInsecureWarningForHttps() =
        runComposeUiTest {
            setContent { TestSettingsScreen(state) }
            onNodeWithTag(SettingsScreenTags.INSECURE_WARNING).assertDoesNotExist()
        }

    @Test
    fun saveAndTestButtonsInvokeCallbacks() =
        runComposeUiTest {
            var saves = 0
            var tests = 0
            var backs = 0
            setContent { TestSettingsScreen(state, onSave = { saves++ }, onTestConnection = { tests++ }, onBack = { backs++ }) }

            onNodeWithTag(SettingsScreenTags.SAVE).performScrollTo().performClick()
            onNodeWithTag(SettingsScreenTags.TEST_CONNECTION).performScrollTo().performClick()
            onNodeWithTag(SettingsScreenTags.BACK).performClick()

            assertEquals(listOf(1, 1, 1), listOf(saves, tests, backs))
        }

    @Test
    fun backButtonHasMinTouchTargetAndCallsOnBackOnce() =
        runComposeUiTest {
            var backs = 0
            setContent { TestSettingsScreen(state, onBack = { backs++ }) }

            val back = onNodeWithTag(SettingsScreenTags.BACK).assertHasClickAction()
            val touchBounds = back.fetchSemanticsNode().touchBoundsInRoot
            val minPx = with(density) { 48.dp.toPx() }
            assertTrue(touchBounds.width >= minPx && touchBounds.height >= minPx, "touch target was $touchBounds")
            back.performClick()

            assertEquals(1, backs)
        }

    @Test
    fun appBarStaysVisibleWhenFormIsScrolled() =
        runComposeUiTest {
            setContent { TestSettingsScreen(state) }

            onNodeWithTag(SettingsScreenTags.SAVE).performScrollTo()

            onNodeWithTag(SettingsScreenTags.TITLE).assertIsDisplayed()
            onNodeWithTag(SettingsScreenTags.BACK).assertIsDisplayed()
        }

    @Test
    fun titleIsAHeading() =
        runComposeUiTest {
            setContent { TestSettingsScreen(state) }
            onNodeWithTag(SettingsScreenTags.TITLE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }

    @Test
    fun savingDisablesSaveButton() =
        runComposeUiTest {
            setContent { TestSettingsScreen(state.copy(saveStatus = SaveStatus.Saving)) }
            onNodeWithTag(SettingsScreenTags.SAVE).assertIsNotEnabled()
        }

    @Test
    fun saveStatusShownOnlyForSavedAndFailed() =
        runComposeUiTest {
            var current by mutableStateOf(state)
            setContent { TestSettingsScreen(current) }
            SaveStatus.entries.forEach { status ->
                current = state.copy(saveStatus = status)
                waitForIdle()
                val node = onNodeWithTag(SettingsScreenTags.SAVE_STATUS)
                if (status == SaveStatus.Saved || status == SaveStatus.Failed) node.assertExists() else node.assertDoesNotExist()
            }
        }

    @Test
    fun testingDisablesTestButtonAndShowsStatus() =
        runComposeUiTest {
            setContent { TestSettingsScreen(state.copy(connection = ConnectionTestState.Testing)) }

            onNodeWithTag(SettingsScreenTags.TEST_CONNECTION).assertIsNotEnabled()
            onNodeWithTag(SettingsScreenTags.CONNECTION_STATUS).assertExists()
        }

    @Test
    fun idleConnectionHasNoStatusAndEnabledButton() =
        runComposeUiTest {
            setContent { TestSettingsScreen(state) }

            onNodeWithTag(SettingsScreenTags.TEST_CONNECTION).assertIsEnabled()
            onNodeWithTag(SettingsScreenTags.CONNECTION_STATUS).assertDoesNotExist()
        }

    @Test
    fun everyConnectionResultShowsStatus() =
        runComposeUiTest {
            var current by mutableStateOf(state)
            setContent { TestSettingsScreen(current) }
            listOf(
                ConnectionTestResult.Success,
                ConnectionTestResult.Unauthorized,
                ConnectionTestResult.Network,
                ConnectionTestResult.Api(null),
                ConnectionTestResult.Api(500),
                ConnectionTestResult.Unexpected,
            ).forEach { result ->
                current = state.copy(connection = ConnectionTestState.Done(result))
                waitForIdle()
                onNodeWithTag(SettingsScreenTags.CONNECTION_STATUS).performScrollTo().assertExists()
            }
        }
}
