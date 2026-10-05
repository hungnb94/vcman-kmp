package com.tekome.vcman.ui

import androidx.compose.runtime.Composable
import com.tekome.vcman.data.ConnectionTestResult
import com.tekome.vcman.data.LlmProviderType
import com.tekome.vcman.data.SettingsFieldError
import org.jetbrains.compose.resources.stringResource
import vcman.sharedui.generated.resources.Res
import vcman.sharedui.generated.resources.settings_connection_api
import vcman.sharedui.generated.resources.settings_connection_api_status
import vcman.sharedui.generated.resources.settings_connection_network
import vcman.sharedui.generated.resources.settings_connection_success
import vcman.sharedui.generated.resources.settings_connection_unauthorized
import vcman.sharedui.generated.resources.settings_connection_unexpected
import vcman.sharedui.generated.resources.settings_error_api_key_blank
import vcman.sharedui.generated.resources.settings_error_base_url_invalid
import vcman.sharedui.generated.resources.settings_error_model_blank
import vcman.sharedui.generated.resources.settings_provider_option

/** Exhaustive on purpose: a new [SettingsFieldError] does not build until it has a message. */
@Composable
internal fun SettingsFieldError.asText(): String =
    when (this) {
        SettingsFieldError.ApiKeyBlank -> stringResource(Res.string.settings_error_api_key_blank)
        SettingsFieldError.BaseUrlInvalid -> stringResource(Res.string.settings_error_base_url_invalid)
        SettingsFieldError.ModelBlank -> stringResource(Res.string.settings_error_model_blank)
    }

@Composable
internal fun ConnectionTestResult.asText(): String =
    when (this) {
        ConnectionTestResult.Success -> stringResource(Res.string.settings_connection_success)
        ConnectionTestResult.Unauthorized -> stringResource(Res.string.settings_connection_unauthorized)
        ConnectionTestResult.Network -> stringResource(Res.string.settings_connection_network)
        is ConnectionTestResult.Api ->
            httpStatus?.let { stringResource(Res.string.settings_connection_api_status, it) }
                ?: stringResource(Res.string.settings_connection_api)

        ConnectionTestResult.Unexpected -> stringResource(Res.string.settings_connection_unexpected)
    }

/** Brand names are not translated; only the "-compatible" wrapper is localized, so providers need no per-entry text. */
@Composable
internal fun LlmProviderType.label(): String = stringResource(Res.string.settings_provider_option, brand)
