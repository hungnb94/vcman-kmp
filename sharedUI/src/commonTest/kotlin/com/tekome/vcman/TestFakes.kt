package com.tekome.vcman

import androidx.lifecycle.ViewModel
import com.tekome.vcman.data.ApiKey
import com.tekome.vcman.data.LlmProviderType
import com.tekome.vcman.data.LlmSettings
import com.tekome.vcman.data.SettingsRepository
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import kotlin.reflect.KClass

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

internal class TestViewModelFactory(
    vararg providers: Pair<KClass<out ViewModel>, () -> ViewModel>,
) : MetroViewModelFactory() {
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel> = providers.toMap()
}
