package com.tekome.vcman.data

internal class FakeSettingsRepository(
    var settings: LlmSettings? = null,
    var failOnSave: Boolean = false,
    var failOnLoad: Boolean = false,
) : SettingsRepository {
    var saveCount = 0
        private set

    override suspend fun load(): LlmSettings? {
        if (failOnLoad) error("load failed")
        return settings
    }

    override suspend fun save(settings: LlmSettings) {
        if (failOnSave) error("save failed")
        saveCount++
        this.settings = settings
    }
}

internal class FakeSecretStore(
    val values: MutableMap<String, String> = mutableMapOf(),
    var failOnPut: Boolean = false,
) : SecretStore {
    override suspend fun get(name: String): String? = values[name]

    override suspend fun put(
        name: String,
        value: String,
    ) {
        if (failOnPut) error("put failed")
        values[name] = value
    }

    override suspend fun remove(name: String) {
        values.remove(name)
    }
}

internal fun validSettings(
    providerType: LlmProviderType = LlmProviderType.AnthropicCompatible,
    key: String = "sk-valid-key",
) = LlmSettings(providerType, ApiKey(key), providerType.defaultBaseUrl, providerType.defaultModel)
