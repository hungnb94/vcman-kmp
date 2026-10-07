package com.tekome.vcman.data

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.SuspendSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException

@OptIn(ExperimentalSettingsApi::class)
internal class KeystoreSecretStore(
    private val storage: SuspendSettings,
    private val cipher: AesGcmSecretCipher,
    private val keys: SecretKeyProvider,
    private val keyAlias: String,
) : SecretStore {
    override suspend fun get(name: String): String? = lock.withLock { read(name) }

    private suspend fun read(name: String): String? {
        val encoded = storage.getStringOrNull(name) ?: return null
        val decrypted =
            try {
                cipher.decrypt(encoded, name)
            } catch (_: SecretTemporarilyUnavailableException) {
                return null
            }
        return decrypted ?: run {
            storage.remove(name)
            try {
                keys.delete(keyAlias)
            } catch (_: GeneralSecurityException) {
            } catch (_: ProviderException) {
            } catch (_: IOException) {
            }
            null
        }
    }

    override suspend fun put(
        name: String,
        value: String,
    ) = lock.withLock {
        val encoded =
            try {
                cipher.encrypt(value, name)
            } catch (_: GeneralSecurityException) {
                encryptWithFreshKey(value, name)
            } catch (_: ProviderException) {
                encryptWithFreshKey(value, name)
            }
        storage.putString(name, encoded)
    }

    override suspend fun remove(name: String) = lock.withLock { storage.remove(name) }

    private companion object {
        val lock = Mutex()
    }

    private fun encryptWithFreshKey(
        value: String,
        name: String,
    ): String {
        keys.delete(keyAlias)
        return cipher.encrypt(value, name)
    }
}
