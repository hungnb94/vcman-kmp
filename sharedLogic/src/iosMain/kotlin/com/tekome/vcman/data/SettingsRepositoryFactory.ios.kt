package com.tekome.vcman.data

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.coroutines.toSuspendSettings
import platform.Foundation.NSUserDefaults

private const val KEYCHAIN_SERVICE = "com.tekome.vcman.llm"

/** Production wiring: `NSUserDefaults` for non-secret values, the Keychain for the API key. */
@OptIn(ExperimentalSettingsImplementation::class, ExperimentalSettingsApi::class)
fun createSettingsRepository(): SettingsRepository =
    StoredSettingsRepository(
        prefs = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults).toSuspendSettings(),
        secrets = SettingsSecretStore(KeychainSettings(service = KEYCHAIN_SERVICE).toSuspendSettings()),
    )
