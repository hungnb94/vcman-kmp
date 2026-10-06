package com.tekome.vcman.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.datastore.DataStoreSettings

private const val KEY_ALIAS = "vcman_llm_api_key"

// DataStore allows one instance per file, so each store lives in a property delegate that caches it per process.
private val Context.llmPreferences by preferencesDataStore(name = "vcman_llm_settings")
private val Context.llmSecrets by preferencesDataStore(name = "vcman_llm_secrets")

/** Production wiring: DataStore for non-secret values, a Keystore-encrypted DataStore for the API key. */
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
