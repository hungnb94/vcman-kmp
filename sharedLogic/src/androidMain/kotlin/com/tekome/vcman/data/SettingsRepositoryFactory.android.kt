package com.tekome.vcman.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.datastore.DataStoreSettings

private const val KEY_ALIAS = "vcman_llm_api_key"

private val Context.llmPreferences by preferencesDataStore(name = "vcman_llm_settings")
private val Context.llmSecrets by preferencesDataStore(name = "vcman_llm_secrets")

@OptIn(ExperimentalSettingsApi::class)
fun createSettingsRepository(context: Context): SettingsRepository {
    val appContext = context.applicationContext
    val keys = AndroidKeystoreKeyProvider()
    return StoredSettingsRepository(
        prefs = DataStoreSettings(appContext.llmPreferences),
        secrets =
            KeystoreSecretStore(
                storage = DataStoreSettings(appContext.llmSecrets),
                cipher = AesGcmSecretCipher(keys, KEY_ALIAS),
                keys = keys,
                keyAlias = KEY_ALIAS,
            ),
    )
}
