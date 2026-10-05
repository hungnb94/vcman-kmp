package com.tekome.vcman.data

/** Immutable LLM connection settings. The key keeps its [ApiKey] type so `toString` never exposes it. */
data class LlmSettings(
    val providerType: LlmProviderType,
    val apiKey: ApiKey,
    val baseUrl: String,
    val model: String,
)

/** The settings inputs a validation error can belong to. */
enum class SettingsField { ApiKey, BaseUrl, Model }

/** A form field that failed validation. UI maps each value to a localized message, shown under [field]. */
enum class SettingsFieldError(
    val field: SettingsField,
) {
    ApiKeyBlank(SettingsField.ApiKey),
    BaseUrlInvalid(SettingsField.BaseUrl),
    ModelBlank(SettingsField.Model),
}

/** Rule table: adding a rule is one enum value plus one line here. */
internal val settingsRules: List<Pair<SettingsFieldError, (LlmSettings) -> Boolean>> =
    listOf(
        SettingsFieldError.ApiKeyBlank to { it.apiKey.value.isBlank() },
        SettingsFieldError.BaseUrlInvalid to { httpHostOf(it.baseUrl) == null },
        SettingsFieldError.ModelBlank to { it.model.isBlank() },
    )

fun LlmSettings.validate(): Set<SettingsFieldError> =
    settingsRules
        .filter { (_, violated) -> violated(this) }
        .mapTo(linkedSetOf()) { (error, _) -> error }
