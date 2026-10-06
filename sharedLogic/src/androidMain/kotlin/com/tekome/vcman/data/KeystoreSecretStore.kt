package com.tekome.vcman.data

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.SuspendSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException

/**
 * [SecretStore] keeping AES-GCM ciphertext in [storage]. When a stored value can never be decrypted again (key lost
 * or permanently invalidated, data corrupted) the ciphertext and the key are discarded, so the secret reads as
 * "not set" and the next [put] starts from a fresh key instead of reusing a broken one. A transient keystore failure
 * reads as "not set" for that call only and discards nothing.
 *
 * Calls are serialized by a process-wide [Mutex]: a [get] that finds an undecryptable value must not discard the ciphertext or
 * key written by a concurrent [put].
 */
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
                return null // transient keystore failure: keep ciphertext and key, the next read may succeed
            }
        return decrypted ?: run {
            storage.remove(name)
            // Best effort: the ciphertext is already gone, so a failing delete must not turn "not set" into a crash.
            // A stale key is harmless; the next put replaces it.
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
        // Process-wide: every instance guards the same DataStore files and Keystore alias, so one lock must cover all.
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
