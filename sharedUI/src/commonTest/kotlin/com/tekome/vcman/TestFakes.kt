package com.tekome.vcman

import com.tekome.vcman.data.ApiKey
import com.tekome.vcman.data.LlmProviderType
import com.tekome.vcman.data.LlmSettings
import com.tekome.vcman.data.SettingsRepository

internal class InMemorySettingsRepository(
    var settings: LlmSettings? = null,
) : SettingsRepository {
    override suspend fun load(): LlmSettings? = settings

    override suspend fun save(settings: LlmSettings) {
        this.settings = settings
    }
}

internal fun configuredSettings(
    providerType: LlmProviderType = LlmProviderType.AnthropicCompatible,
    key: String = "sk-configured",
) = LlmSettings(providerType, ApiKey(key), providerType.defaultBaseUrl, providerType.defaultModel)
