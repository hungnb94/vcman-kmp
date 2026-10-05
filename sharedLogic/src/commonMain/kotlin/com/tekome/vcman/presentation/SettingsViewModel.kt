package com.tekome.vcman.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tekome.vcman.data.ApiKey
import com.tekome.vcman.data.ConnectionTestResult
import com.tekome.vcman.data.ConnectionTester
import com.tekome.vcman.data.LlmConnectionTester
import com.tekome.vcman.data.LlmProviderType
import com.tekome.vcman.data.LlmSettings
import com.tekome.vcman.data.SettingsFieldError
import com.tekome.vcman.data.SettingsRepository
import com.tekome.vcman.data.isInsecureRemote
import com.tekome.vcman.data.validate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The form being edited. The key keeps its [ApiKey] type, so `toString` of the form and of any state holding it is safe.
 */
data class SettingsForm(
    val providerType: LlmProviderType,
    val apiKey: ApiKey,
    val baseUrl: String,
    val model: String,
) {
    /**
     * Replaces only values that are blank or still equal to the previous provider's default, so a customised
     * proxy URL or model survives an accidental provider change. Driven by data, never by the provider's identity.
     */
    fun switchProvider(next: LlmProviderType): SettingsForm =
        copy(
            providerType = next,
            baseUrl = baseUrl.keepIfCustomised(providerType.defaultBaseUrl) ?: next.defaultBaseUrl,
            model = model.keepIfCustomised(providerType.defaultModel) ?: next.defaultModel,
        )

    fun toSettings(): LlmSettings =
        LlmSettings(
            providerType = providerType,
            apiKey = ApiKey(apiKey.value.trim()),
            baseUrl = baseUrl.trim(),
            model = model.trim(),
        )

    private fun String.keepIfCustomised(previousDefault: String): String? = takeUnless { it.isBlank() || it.trim() == previousDefault }

    companion object {
        fun defaultsFor(providerType: LlmProviderType): SettingsForm =
            SettingsForm(providerType, ApiKey(""), providerType.defaultBaseUrl, providerType.defaultModel)

        fun from(settings: LlmSettings): SettingsForm = SettingsForm(settings.providerType, settings.apiKey, settings.baseUrl, settings.model)
    }
}

enum class SaveStatus { Idle, Saving, Saved, Failed }

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState

    data object Testing : ConnectionTestState

    data class Done(
        val result: ConnectionTestResult,
    ) : ConnectionTestState
}

data class SettingsUiState(
    val loaded: Boolean = false,
    val form: SettingsForm = SettingsForm.defaultsFor(LlmProviderType.entries.first()),
    val errors: Set<SettingsFieldError> = emptySet(),
    val saveStatus: SaveStatus = SaveStatus.Idle,
    val connection: ConnectionTestState = ConnectionTestState.Idle,
) {
    /** Plain http to a non-local host: shown as a warning, never blocks saving. */
    val insecureWarning: Boolean get() = isInsecureRemote(form.baseUrl)
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val connectionTester: ConnectionTester = LlmConnectionTester(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Field errors stay hidden while typing until the user first tries to save or test. */
    private var errorsVisible = false

    /** Set on the first user edit; a load that finishes afterwards keeps the user's input. */
    private var edited = false

    init {
        viewModelScope.launch {
            val saved =
                try {
                    repository.load()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            _uiState.update {
                // A slow load must not overwrite what the user has already typed.
                if (edited) {
                    it.copy(loaded = true)
                } else {
                    it.copy(
                        loaded = true,
                        form = saved?.let(SettingsForm::from) ?: SettingsForm.defaultsFor(LlmProviderType.entries.first()),
                    )
                }
            }
        }
    }

    fun selectProvider(providerType: LlmProviderType) = edit { it.switchProvider(providerType) }

    fun updateApiKey(value: String) = edit { it.copy(apiKey = ApiKey(value)) }

    fun updateBaseUrl(value: String) = edit { it.copy(baseUrl = value) }

    fun updateModel(value: String) = edit { it.copy(model = value) }

    fun save() {
        val settings = validatedSettings() ?: return
        if (_uiState.value.saveStatus == SaveStatus.Saving) return
        _uiState.update { it.copy(saveStatus = SaveStatus.Saving) }
        viewModelScope.launch {
            val status =
                try {
                    repository.save(settings)
                    SaveStatus.Saved
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    SaveStatus.Failed
                }
            _uiState.update { it.copy(saveStatus = status) }
        }
    }

    fun testConnection() {
        if (_uiState.value.connection == ConnectionTestState.Testing) return
        val settings = validatedSettings() ?: return
        _uiState.update { it.copy(connection = ConnectionTestState.Testing) }
        viewModelScope.launch {
            val result =
                try {
                    connectionTester.test(settings)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ConnectionTestResult.Unexpected
                }
            _uiState.update { it.copy(connection = ConnectionTestState.Done(result)) }
        }
    }

    /** The trimmed settings if the form is valid; otherwise publishes the errors and returns `null`. */
    private fun validatedSettings(): LlmSettings? {
        val settings = _uiState.value.form.toSettings()
        val errors = settings.validate()
        errorsVisible = true
        _uiState.update { it.copy(errors = errors) }
        return settings.takeIf { errors.isEmpty() }
    }

    private fun edit(change: (SettingsForm) -> SettingsForm) {
        edited = true
        _uiState.update { state ->
            val form = change(state.form)
            state.copy(
                form = form,
                errors = if (errorsVisible) form.toSettings().validate() else state.errors,
                saveStatus = SaveStatus.Idle,
                connection = if (state.connection is ConnectionTestState.Testing) state.connection else ConnectionTestState.Idle,
            )
        }
    }
}
