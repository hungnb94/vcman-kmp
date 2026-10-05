package com.tekome.vcman.data

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings

private const val PREFS_FILE = "vcman_llm_settings"
private const val SECRETS_FILE = "vcman_llm_secrets"
private const val KEY_ALIAS = "vcman_llm_api_key"

/** Production wiring: plain preferences for non-secret values, Keystore-encrypted preferences for the API key. */
fun createSettingsRepository(context: Context): SettingsRepository {
    val appContext = context.applicationContext
    val keys = AndroidKeystoreKeyProvider()
    return StoredSettingsRepository(
        prefs = SharedPreferencesSettings(appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)),
        secrets =
            KeystoreSecretStore(
                storage = SharedPreferencesSettings(appContext.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE)),
                cipher = AesGcmSecretCipher(keys, KEY_ALIAS),
                keys = keys,
                keyAlias = KEY_ALIAS,
            ),
    )
}
