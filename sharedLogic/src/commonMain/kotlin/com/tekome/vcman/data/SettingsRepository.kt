package com.tekome.vcman.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SettingsRepository {
    suspend fun load(): LlmSettings?

    suspend fun save(settings: LlmSettings)
}

internal interface SecretStore {
    fun get(name: String): String?

    fun put(
        name: String,
        value: String,
    )

    fun remove(name: String)
}

internal class SettingsSecretStore(
    private val settings: Settings,
) : SecretStore {
    override fun get(name: String): String? = settings.getStringOrNull(name)

    override fun put(
        name: String,
        value: String,
    ) = settings.putString(name, value)

    override fun remove(name: String) = settings.remove(name)
}

internal class StoredSettingsRepository(
    private val prefs: Settings,
    private val secrets: SecretStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SettingsRepository {
    override suspend fun load(): LlmSettings? =
        withContext(dispatcher) {
            val providerType = LlmProviderType.fromId(prefs.getStringOrNull(KEY_PROVIDER_ID)) ?: return@withContext null
            LlmSettings(
                providerType = providerType,
                apiKey = ApiKey(secrets.get(SECRET_API_KEY).orEmpty()),
                baseUrl = prefs.getString(KEY_BASE_URL, providerType.defaultBaseUrl),
                model = prefs.getString(KEY_MODEL, providerType.defaultModel),
            )
        }

    override suspend fun save(settings: LlmSettings) =
        withContext(dispatcher) {
            if (settings.apiKey.value.isEmpty()) {
                secrets.remove(SECRET_API_KEY)
            } else {
                secrets.put(SECRET_API_KEY, settings.apiKey.value)
            }
            prefs.putString(KEY_PROVIDER_ID, settings.providerType.id)
            prefs.putString(KEY_BASE_URL, settings.baseUrl)
            prefs.putString(KEY_MODEL, settings.model)
        }

    private companion object {
        const val KEY_PROVIDER_ID = "llm.provider_id"
        const val KEY_BASE_URL = "llm.base_url"
        const val KEY_MODEL = "llm.model"
        const val SECRET_API_KEY = "llm.api_key"
    }
}
