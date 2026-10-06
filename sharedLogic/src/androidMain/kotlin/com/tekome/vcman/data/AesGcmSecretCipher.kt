package com.tekome.vcman.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The keystore failed in a way that may succeed later; the stored secret is still intact. */
internal class SecretTemporarilyUnavailableException(
    cause: Throwable,
) : Exception(cause)

/** Supplies the AES key: Android Keystore in production, a software key in tests. */
internal interface SecretKeyProvider {
    fun getOrCreate(alias: String): SecretKey

    /** Forgets the key so the next [getOrCreate] generates a fresh one. */
    fun delete(alias: String)
}

internal class AndroidKeystoreKeyProvider : SecretKeyProvider {
    override fun getOrCreate(alias: String): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    override fun delete(alias: String) {
        keyStore().deleteEntry(alias)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_SIZE_BITS = 256
    }
}

/**
 * AES-256-GCM with the layout `base64(version(1) | iv(12) | ciphertext+tag)`. The IV is generated randomly by the
 * [Cipher] (never supplied), and the entry name is bound as AAD so ciphertexts cannot be swapped between entries.
 */
internal class AesGcmSecretCipher(
    private val keys: SecretKeyProvider,
    private val keyAlias: String,
) {
    /**
     * If the key was permanently invalidated it is discarded and regenerated once, so saving works again
     * (values sealed with the old key were already unrecoverable).
     */
    fun encrypt(
        plaintext: String,
        entryName: String,
    ): String =
        try {
            seal(plaintext, entryName)
        } catch (_: InvalidKeyException) {
            keys.delete(keyAlias)
            seal(plaintext, entryName)
        } catch (_: UnrecoverableKeyException) {
            keys.delete(keyAlias)
            seal(plaintext, entryName)
        }

    private fun seal(
        plaintext: String,
        entryName: String,
    ): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.getOrCreate(keyAlias))
        cipher.updateAAD(entryName.encodeToByteArray())
        val sealed = cipher.doFinal(plaintext.encodeToByteArray())
        return Base64.getEncoder().encodeToString(byteArrayOf(FORMAT_V1) + cipher.iv + sealed)
    }

    /**
     * `null` when the value can never be decrypted again (tampered, malformed, key lost or permanently invalidated).
     *
     * @throws SecretTemporarilyUnavailableException when the keystore failed in a way that may succeed on retry,
     * so the caller must not discard the stored value.
     */
    fun decrypt(
        encoded: String,
        entryName: String,
    ): String? =
        try {
            val bytes = Base64.getDecoder().decode(encoded)
            if (bytes.size < HEADER_BYTES + TAG_BYTES || bytes[0] != FORMAT_V1) {
                null
            } else {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    keys.getOrCreate(keyAlias),
                    GCMParameterSpec(TAG_BYTES * Byte.SIZE_BITS, bytes, 1, IV_BYTES),
                )
                cipher.updateAAD(entryName.encodeToByteArray())
                cipher.doFinal(bytes, HEADER_BYTES, bytes.size - HEADER_BYTES).decodeToString()
            }
        } catch (_: AEADBadTagException) {
            null
        } catch (_: InvalidKeyException) {
            null
        } catch (_: UnrecoverableKeyException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (e: GeneralSecurityException) {
            throw SecretTemporarilyUnavailableException(e)
        } catch (e: ProviderException) {
            throw SecretTemporarilyUnavailableException(e)
        } catch (e: IOException) {
            throw SecretTemporarilyUnavailableException(e)
        }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_V1: Byte = 1
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
        const val HEADER_BYTES = 1 + IV_BYTES
    }
}
