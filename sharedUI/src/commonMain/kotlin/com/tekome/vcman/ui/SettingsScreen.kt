package com.tekome.vcman.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tekome.vcman.data.ConnectionTestResult
import com.tekome.vcman.data.LlmProviderType
import com.tekome.vcman.data.SettingsField
import com.tekome.vcman.presentation.ConnectionTestState
import com.tekome.vcman.presentation.SaveStatus
import com.tekome.vcman.presentation.SettingsUiState
import org.jetbrains.compose.resources.stringResource
import vcman.sharedui.generated.resources.Res
import vcman.sharedui.generated.resources.settings_api_key_label
import vcman.sharedui.generated.resources.settings_back
import vcman.sharedui.generated.resources.settings_base_url_label
import vcman.sharedui.generated.resources.settings_connection_testing
import vcman.sharedui.generated.resources.settings_hide_key
import vcman.sharedui.generated.resources.settings_model_label
import vcman.sharedui.generated.resources.settings_provider_label
import vcman.sharedui.generated.resources.settings_save
import vcman.sharedui.generated.resources.settings_save_failed
import vcman.sharedui.generated.resources.settings_saved
import vcman.sharedui.generated.resources.settings_show_key
import vcman.sharedui.generated.resources.settings_test_connection
import vcman.sharedui.generated.resources.settings_title
import vcman.sharedui.generated.resources.settings_warning_insecure_http

internal object SettingsScreenTags {
    const val TITLE = "settings_title"
    const val BACK = "settings_back"
    const val PROVIDER_PREFIX = "settings_provider_"
    const val API_KEY = "settings_api_key"
    const val TOGGLE_KEY = "settings_toggle_key"
    const val BASE_URL = "settings_base_url"
    const val MODEL = "settings_model"
    const val ERROR_PREFIX = "settings_error_"
    const val INSECURE_WARNING = "settings_insecure_warning"
    const val SAVE = "settings_save"
    const val SAVE_STATUS = "settings_save_status"
    const val TEST_CONNECTION = "settings_test_connection"
    const val CONNECTION_STATUS = "settings_connection_status"

    fun provider(type: LlmProviderType) = PROVIDER_PREFIX + type.id
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    onSelectProvider: (LlmProviderType) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onBack: () -> Unit,
) {
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    val form = state.form

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.testTag(SettingsScreenTags.BACK)) {
                Text(stringResource(Res.string.settings_back))
            }
            Text(
                text = stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag(SettingsScreenTags.TITLE).semantics { heading() },
            )
        }

        Text(
            stringResource(Res.string.settings_provider_label),
            style = MaterialTheme.typography.titleSmall,
        )
        Column(modifier = Modifier.selectableGroup()) {
            LlmProviderType.entries.forEach { type ->
                val selected = type == form.providerType
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelectProvider(type) },
                            ).testTag(SettingsScreenTags.provider(type)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(text = type.label(), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        SettingsTextField(
            value = form.apiKey.value,
            onValueChange = onApiKeyChange,
            label = stringResource(Res.string.settings_api_key_label),
            state = state,
            field = SettingsField.ApiKey,
            tag = SettingsScreenTags.API_KEY,
            masked = !keyVisible,
        )
        TextButton(
            onClick = { keyVisible = !keyVisible },
            modifier = Modifier.align(Alignment.End).testTag(SettingsScreenTags.TOGGLE_KEY),
        ) {
            Text(stringResource(if (keyVisible) Res.string.settings_hide_key else Res.string.settings_show_key))
        }
        SettingsTextField(
            value = form.baseUrl,
            onValueChange = onBaseUrlChange,
            label = stringResource(Res.string.settings_base_url_label),
            state = state,
            field = SettingsField.BaseUrl,
            tag = SettingsScreenTags.BASE_URL,
            keyboardType = KeyboardType.Uri,
        )
        if (state.insecureWarning) {
            Text(
                text = stringResource(Res.string.settings_warning_insecure_http),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(SettingsScreenTags.INSECURE_WARNING),
            )
        }
        SettingsTextField(
            value = form.model,
            onValueChange = onModelChange,
            label = stringResource(Res.string.settings_model_label),
            state = state,
            field = SettingsField.Model,
            tag = SettingsScreenTags.MODEL,
        )

        Button(
            onClick = onSave,
            enabled = state.loaded && state.saveStatus != SaveStatus.Saving,
            modifier = Modifier.fillMaxWidth().testTag(SettingsScreenTags.SAVE),
        ) {
            Text(stringResource(Res.string.settings_save))
        }
        when (state.saveStatus) {
            SaveStatus.Saved -> {
                StatusText(
                    stringResource(Res.string.settings_saved),
                    SettingsScreenTags.SAVE_STATUS,
                )
            }

            SaveStatus.Failed -> {
                StatusText(
                    stringResource(Res.string.settings_save_failed),
                    SettingsScreenTags.SAVE_STATUS,
                    isError = true,
                )
            }

            SaveStatus.Idle, SaveStatus.Saving -> {
            }
        }

        OutlinedButton(
            onClick = onTestConnection,
            enabled = state.loaded && state.connection != ConnectionTestState.Testing,
            modifier = Modifier.fillMaxWidth().testTag(SettingsScreenTags.TEST_CONNECTION),
        ) {
            Text(stringResource(Res.string.settings_test_connection))
        }
        when (val connection = state.connection) {
            ConnectionTestState.Idle -> {
            }

            ConnectionTestState.Testing -> {
                StatusText(
                    stringResource(Res.string.settings_connection_testing),
                    SettingsScreenTags.CONNECTION_STATUS,
                )
            }

            is ConnectionTestState.Done -> {
                StatusText(
                    text = connection.result.asText(),
                    tag = SettingsScreenTags.CONNECTION_STATUS,
                    isError = connection.result != ConnectionTestResult.Success,
                )
            }
        }
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    state: SettingsUiState,
    field: SettingsField,
    tag: String,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val errors = state.errors.filter { it.field == field }
    key(masked) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = errors.isNotEmpty(),
            supportingText =
                if (errors.isEmpty()) {
                    null
                } else {
                    {
                        Column {
                            errors.forEach { error ->
                                Text(
                                    text = error.asText(),
                                    modifier = Modifier.testTag(SettingsScreenTags.ERROR_PREFIX + error.name),
                                )
                            }
                        }
                    }
                },
            visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (masked) KeyboardType.Password else keyboardType,
                    autoCorrectEnabled = false,
                ),
            modifier =
                modifier
                    .fillMaxWidth()
                    .then(if (masked) Modifier.semantics { password() } else Modifier)
                    .testTag(tag),
        )
    }
}

@Composable
private fun StatusText(
    text: String,
    tag: String,
    isError: Boolean = false,
) {
    Text(
        text = text,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .testTag(tag)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
@Preview
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen(
            state = SettingsUiState(loaded = true),
            onSelectProvider = {},
            onApiKeyChange = {},
            onBaseUrlChange = {},
            onModelChange = {},
            onSave = {},
            onTestConnection = {},
            onBack = {},
        )
    }
}
