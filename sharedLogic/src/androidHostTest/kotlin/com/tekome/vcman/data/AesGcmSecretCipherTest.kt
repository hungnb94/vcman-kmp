package com.tekome.vcman.data

import java.io.IOException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** Software-key [SecretKeyProvider] (the Android Keystore is unavailable on the JVM) that counts deletions. */
internal class SoftwareKeyProvider : SecretKeyProvider {
    private val keys = mutableMapOf<String, SecretKey>()
    var deleteCount = 0
        private set
    var failNextGetOrCreate: Exception? = null

    override fun getOrCreate(alias: String): SecretKey {
        failNextGetOrCreate?.let {
            failNextGetOrCreate = null
            throw it
        }
        return keys.getOrPut(alias) { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }
    }

    override fun delete(alias: String) {
        deleteCount++
        keys.remove(alias)
    }
}

class AesGcmSecretCipherTest {
    private val keys = SoftwareKeyProvider()
    private val cipher = AesGcmSecretCipher(keys, "llm")

    private fun tamper(
        encoded: String,
        index: (ByteArray) -> Int,
    ): String {
        val bytes = Base64.getDecoder().decode(encoded)
        bytes[index(bytes)] = (bytes[index(bytes)].toInt() xor 1).toByte()
        return Base64.getEncoder().encodeToString(bytes)
    }

    @Test
    fun roundTripsEmptyShortLongAndUnicode() {
        listOf("", "sk-ant-123", "k".repeat(10_000), "khóa-bí-mật-🔑").forEach {
            assertEquals(it, cipher.decrypt(cipher.encrypt(it, "api_key"), "api_key"))
        }
    }

    @Test
    fun usesFreshIvEachTime() {
        val a = cipher.encrypt("sk-secret", "api_key")
        val b = cipher.encrypt("sk-secret", "api_key")

        assertNotEquals(a, b)
        val ivA = Base64.getDecoder().decode(a).copyOfRange(1, 13).toList()
        val ivB = Base64.getDecoder().decode(b).copyOfRange(1, 13).toList()
        assertNotEquals(ivA, ivB)
    }

    @Test
    fun outputDoesNotContainPlaintext() {
        val encoded = cipher.encrypt("sk-secret", "api_key")

        assertFalse("sk-secret" in encoded)
        assertFalse("sk-secret" in Base64.getDecoder().decode(encoded).decodeToString())
    }

    @Test
    fun flippedBitInVersionIvOrTagReturnsNull() {
        val encoded = cipher.encrypt("sk", "api_key")

        listOf<(ByteArray) -> Int>({ 0 }, { 1 }, { 12 }, { it.lastIndex }).forEach { position ->
            assertNull(cipher.decrypt(tamper(encoded, position), "api_key"))
        }
    }

    @Test
    fun lostKeyReturnsNull() {
        val stored = cipher.encrypt("sk", "api_key")

        assertNull(AesGcmSecretCipher(SoftwareKeyProvider(), "llm").decrypt(stored, "api_key"))
    }

    @Test
    fun differentEntryNameReturnsNull() {
        assertNull(cipher.decrypt(cipher.encrypt("sk", "api_key"), "other_entry"))
    }

    @Test
    fun malformedInputReturnsNull() {
        listOf(
            "",
            "%%%not-base64",
            Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)),
            Base64.getEncoder().encodeToString(ByteArray(40) { 9 }),
        ).forEach { assertNull(cipher.decrypt(it, "api_key"), it) }
    }

    @Test
    fun invalidatedKeysAreTreatedAsLost() {
        val stored = cipher.encrypt("sk", "api_key")

        listOf(InvalidKeyException("invalidated"), UnrecoverableKeyException("gone")).forEach {
            keys.failNextGetOrCreate = it
            assertNull(cipher.decrypt(stored, "api_key"), it.toString())
        }
    }

    @Test
    fun transientKeystoreFailuresAreSignalledNotTreatedAsLost() {
        val stored = cipher.encrypt("sk", "api_key")

        listOf(ProviderException("keystore busy"), IOException("io"), GeneralSecurityException("other")).forEach {
            keys.failNextGetOrCreate = it
            assertFailsWith<SecretTemporarilyUnavailableException>(it.toString()) { cipher.decrypt(stored, "api_key") }
        }
    }
}
