package com.tekome.vcman.data

import com.russhwolf.settings.Settings
import java.security.GeneralSecurityException
import java.security.ProviderException

/**
 * [SecretStore] keeping AES-GCM ciphertext in [storage]. When a stored value can never be decrypted again (key lost
 * or permanently invalidated, data corrupted) the ciphertext and the key are discarded, so the secret reads as "not set" and the
 * next [put] starts from a fresh key instead of reusing a broken one. A transient keystore failure reads as "not set"
 * for that call only and discards nothing.
 */
internal class KeystoreSecretStore(
    private val storage: Settings,
    private val cipher: AesGcmSecretCipher,
    private val keys: SecretKeyProvider,
    private val keyAlias: String,
) : SecretStore {
    override fun get(name: String): String? {
        val encoded = storage.getStringOrNull(name) ?: return null
        val decrypted =
            try {
                cipher.decrypt(encoded, name)
            } catch (_: SecretTemporarilyUnavailableException) {
                return null // transient keystore failure: keep ciphertext and key, the next read may succeed
            }
        return decrypted ?: run {
            storage.remove(name)
            keys.delete(keyAlias)
            null
        }
    }

    override fun put(
        name: String,
        value: String,
    ) {
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

    override fun remove(name: String) = storage.remove(name)

    private fun encryptWithFreshKey(
        value: String,
        name: String,
    ): String {
        keys.delete(keyAlias)
        return cipher.encrypt(value, name)
    }
}
