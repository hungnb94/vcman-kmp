package com.tekome.vcman.presentation

import com.tekome.vcman.data.ApiKey
import com.tekome.vcman.data.ConnectionTestResult
import com.tekome.vcman.data.ConnectionTester
import com.tekome.vcman.data.FakeSettingsRepository
import com.tekome.vcman.data.LlmProviderType
import com.tekome.vcman.data.LlmSettings
import com.tekome.vcman.data.SettingsFieldError
import com.tekome.vcman.data.SettingsRepository
import com.tekome.vcman.data.validSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private class FakeTester(
        private val pending: CompletableDeferred<ConnectionTestResult> = CompletableDeferred(),
    ) : ConnectionTester {
        val tested = mutableListOf<LlmSettings>()

        override suspend fun test(settings: LlmSettings): ConnectionTestResult {
            tested += settings
            return pending.await()
        }

        fun complete(result: ConnectionTestResult) = pending.complete(result)
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeSettingsRepository = FakeSettingsRepository(),
        tester: ConnectionTester = FakeTester(),
    ) = SettingsViewModel(repository, tester)

    private fun SettingsViewModel.fillValid() {
        updateApiKey("  sk-typed  ")
        updateBaseUrl("  https://proxy.example.com/v1  ")
        updateModel("  my-model ")
    }

    @Test
    fun emptyRepositoryShowsDefaultsOfFirstProvider() {
        val state = viewModel().uiState.value

        val first = LlmProviderType.entries.first()
        assertTrue(state.loaded)
        assertEquals(SettingsForm.defaultsFor(first), state.form)
        assertEquals(ApiKey(""), state.form.apiKey)
    }

    @Test
    fun savedSettingsAreShownAgain() {
        val saved = LlmSettings(LlmProviderType.AnthropicCompatible, ApiKey("sk-saved"), "https://gw.example.com", "claude-x")

        val state = viewModel(FakeSettingsRepository(saved)).uiState.value

        assertEquals(SettingsForm.from(saved), state.form)
        assertEquals("sk-saved", state.form.apiKey.value)
    }

    @Test
    fun slowLoadDoesNotOverwriteUserInput() {
        val gate = CompletableDeferred<Unit>()
        val saved = validSettings(key = "sk-saved")
        val repository =
            object : SettingsRepository {
                override suspend fun load(): LlmSettings? {
                    gate.await()
                    return saved
                }

                override suspend fun save(settings: LlmSettings) = Unit
            }
        val vm = SettingsViewModel(repository, FakeTester())

        vm.updateApiKey("sk-typed")
        gate.complete(Unit)

        val state = vm.uiState.value
        assertTrue(state.loaded)
        assertEquals("sk-typed", state.form.apiKey.value)
    }

    @Test
    fun loadFailureFallsBackToDefaults() {
        val state = viewModel(FakeSettingsRepository(validSettings(), failOnLoad = true)).uiState.value

        assertTrue(state.loaded)
        assertEquals(SettingsForm.defaultsFor(LlmProviderType.entries.first()), state.form)
    }

    @Test
    fun selectingProviderAppliesItsDefaultsForEveryEntry() {
        LlmProviderType.entries.forEach { type ->
            val vm = viewModel()

            vm.selectProvider(type)

            val form = vm.uiState.value.form
            assertEquals(type, form.providerType)
            assertEquals(type.defaultBaseUrl, form.baseUrl)
            assertEquals(type.defaultModel, form.model)
        }
    }

    @Test
    fun switchingProviderKeepsCustomisedBaseUrlAndModel() {
        val vm = viewModel()
        vm.selectProvider(LlmProviderType.OpenAICompatible)
        vm.updateBaseUrl("http://localhost:11434/v1")
        vm.updateModel("llama3")

        vm.selectProvider(LlmProviderType.AnthropicCompatible)

        val form = vm.uiState.value.form
        assertEquals("http://localhost:11434/v1", form.baseUrl)
        assertEquals("llama3", form.model)
    }

    @Test
    fun switchingProviderFillsBlankFields() {
        val vm = viewModel()
        vm.selectProvider(LlmProviderType.OpenAICompatible)
        vm.updateBaseUrl("")
        vm.updateModel("  ")

        vm.selectProvider(LlmProviderType.AnthropicCompatible)

        assertEquals(LlmProviderType.AnthropicCompatible.defaultBaseUrl, vm.uiState.value.form.baseUrl)
        assertEquals(LlmProviderType.AnthropicCompatible.defaultModel, vm.uiState.value.form.model)
    }

    @Test
    fun invalidSaveReportsEachFieldAndDoesNotTouchRepository() {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)
        vm.updateApiKey("   ")
        vm.updateBaseUrl("abc")
        vm.updateModel("")

        vm.save()

        assertEquals(SettingsFieldError.entries.toSet(), vm.uiState.value.errors)
        assertEquals(0, repository.saveCount)
        assertEquals(SaveStatus.Idle, vm.uiState.value.saveStatus)
    }

    @Test
    fun errorsAreHiddenWhileTypingUntilFirstSaveThenTrackEdits() {
        val vm = viewModel()
        vm.updateApiKey("")
        assertTrue(vm.uiState.value.errors.isEmpty())

        vm.save()
        assertTrue(SettingsFieldError.ApiKeyBlank in vm.uiState.value.errors)

        vm.updateApiKey("sk-now-valid")
        assertFalse(SettingsFieldError.ApiKeyBlank in vm.uiState.value.errors)
    }

    @Test
    fun validSaveStoresTrimmedSettingsAndReportsSaved() {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)
        vm.selectProvider(LlmProviderType.OpenAICompatible)
        vm.fillValid()

        vm.save()

        assertEquals(LlmSettings(LlmProviderType.OpenAICompatible, ApiKey("sk-typed"), "https://proxy.example.com/v1", "my-model"), repository.settings)
        assertEquals(1, repository.saveCount)
        assertEquals(SaveStatus.Saved, vm.uiState.value.saveStatus)
        assertTrue(vm.uiState.value.errors.isEmpty())
    }

    @Test
    fun repositoryFailureReportsFailed() {
        val vm = viewModel(FakeSettingsRepository(failOnSave = true))
        vm.fillValid()

        vm.save()

        assertEquals(SaveStatus.Failed, vm.uiState.value.saveStatus)
    }

    @Test
    fun editingAfterSaveResetsStatus() {
        val vm = viewModel()
        vm.fillValid()
        vm.save()

        vm.updateModel("another")

        assertEquals(SaveStatus.Idle, vm.uiState.value.saveStatus)
    }

    @Test
    fun savedSettingsAreVisibleToANewViewModel() {
        val repository = FakeSettingsRepository()
        val first = viewModel(repository)
        first.selectProvider(LlmProviderType.AnthropicCompatible)
        first.fillValid()
        first.save()

        val reopened = viewModel(repository)

        assertEquals(first.uiState.value.form.toSettings(), reopened.uiState.value.form.toSettings())
    }

    @Test
    fun insecureWarningFollowsBaseUrlAndNeverBlocksSaving() {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)
        vm.fillValid()

        vm.updateBaseUrl("http://192.168.1.10:11434/v1")
        assertTrue(vm.uiState.value.insecureWarning)
        vm.save()
        assertEquals(1, repository.saveCount)

        vm.updateBaseUrl("http://10.0.2.2:11434/v1")
        assertFalse(vm.uiState.value.insecureWarning)
        vm.updateBaseUrl("https://api.openai.com/v1")
        assertFalse(vm.uiState.value.insecureWarning)
    }

    @Test
    fun testConnectionWithInvalidFormSkipsTesterAndShowsErrors() {
        val tester = FakeTester()
        val vm = viewModel(tester = tester)

        vm.testConnection()

        assertTrue(tester.tested.isEmpty())
        assertTrue(vm.uiState.value.errors.isNotEmpty())
        assertEquals(ConnectionTestState.Idle, vm.uiState.value.connection)
    }

    @Test
    fun testConnectionRunsWithTrimmedUnsavedValuesAndReportsResult() =
        runTest {
            val repository = FakeSettingsRepository()
            val tester = FakeTester()
            val vm = viewModel(repository, tester)
            vm.fillValid()

            vm.testConnection()
            assertEquals(ConnectionTestState.Testing, vm.uiState.value.connection)
            tester.complete(ConnectionTestResult.Unauthorized)

            assertEquals(ConnectionTestState.Done(ConnectionTestResult.Unauthorized), vm.uiState.value.connection)
            assertEquals("sk-typed", tester.tested.single().apiKey.value)
            assertEquals(0, repository.saveCount)
        }

    @Test
    fun secondTestWhileTestingIsNoOp() {
        val tester = FakeTester()
        val vm = viewModel(tester = tester)
        vm.fillValid()

        vm.testConnection()
        vm.testConnection()

        assertEquals(1, tester.tested.size)
    }

    @Test
    fun testerCrashBecomesUnexpected() {
        val vm =
            viewModel(
                tester = ConnectionTester { throw IllegalStateException("boom") },
            )
        vm.fillValid()

        vm.testConnection()

        assertEquals(ConnectionTestState.Done(ConnectionTestResult.Unexpected), vm.uiState.value.connection)
    }

    @Test
    fun editingClearsFinishedConnectionResult() {
        val tester = FakeTester()
        val vm = viewModel(tester = tester)
        vm.fillValid()
        vm.testConnection()
        tester.complete(ConnectionTestResult.Success)
        assertIs<ConnectionTestState.Done>(vm.uiState.value.connection)

        vm.updateModel("changed")

        assertEquals(ConnectionTestState.Idle, vm.uiState.value.connection)
    }

    @Test
    fun uiStateAndFormToStringNeverContainKey() {
        val vm = viewModel(FakeSettingsRepository(validSettings(key = "sk-loaded-secret")))
        vm.updateApiKey("sk-typed-secret")

        val rendered = "${vm.uiState.value} ${vm.uiState.value.form}"

        assertFalse("sk-loaded-secret" in rendered)
        assertFalse("sk-typed-secret" in rendered)
    }
}
