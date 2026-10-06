package com.tekome.vcman.data

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.coroutines.toSuspendSettings
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.security.ProviderException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSettingsApi::class)
class KeystoreSecretStoreTest {
    private val storage = MapSettings()
    private val suspendStorage = storage.toSuspendSettings()
    private val keys = SoftwareKeyProvider()
    private val store = KeystoreSecretStore(suspendStorage, AesGcmSecretCipher(keys, "alias"), keys, "alias")

    @Test
    fun putGetRoundTripAndRemove() =
        runTest {
            store.put("llm.api_key", "sk-1")
            assertEquals("sk-1", store.get("llm.api_key"))

            store.remove("llm.api_key")
            assertNull(store.get("llm.api_key"))
        }

    @Test
    fun missingEntryReadsNullWithoutDeletingKey() =
        runTest {
            assertNull(store.get("llm.api_key"))
            assertEquals(0, keys.deleteCount)
        }

    @Test
    fun storageHoldsOnlyCiphertext() =
        runTest {
            store.put("llm.api_key", "sk-plaintext-secret")

            assertTrue(storage.keys.isNotEmpty())
            assertFalse(storage.keys.any { "sk-plaintext-secret" in storage.getString(it, "") })
        }

    @Test
    fun corruptedEntryIsDroppedTogetherWithKeyExactlyOnce() =
        runTest {
            storage.putString("llm.api_key", "garbage")

            assertNull(store.get("llm.api_key"))
            assertNull(storage.getStringOrNull("llm.api_key"))
            assertEquals(1, keys.deleteCount)

            assertNull(store.get("llm.api_key"))
            assertEquals(1, keys.deleteCount)
        }

    @Test
    fun lostKeyBehavesLikeNotConfiguredAndRecoversOnNextPut() =
        runTest {
            store.put("llm.api_key", "sk-1")
            val afterKeyLoss =
                KeystoreSecretStore(suspendStorage, AesGcmSecretCipher(SoftwareKeyProvider(), "alias"), keys, "alias")

            assertNull(afterKeyLoss.get("llm.api_key"))

            afterKeyLoss.put("llm.api_key", "sk-2")
            assertEquals("sk-2", afterKeyLoss.get("llm.api_key"))
        }

    @Test
    fun failingKeyDeleteStillReadsAsNotSet() =
        runTest {
            storage.putString("llm.api_key", "garbage")
            val failingDelete =
                object : SecretKeyProvider {
                    override fun getOrCreate(alias: String) = keys.getOrCreate(alias)

                    override fun delete(alias: String) = throw KeyStoreException("cannot delete")
                }
            val flaky = KeystoreSecretStore(suspendStorage, AesGcmSecretCipher(failingDelete, "alias"), failingDelete, "alias")

            assertNull(flaky.get("llm.api_key"))
            assertNull(storage.getStringOrNull("llm.api_key"))
        }

    @Test
    fun ioFailureOnKeyDeleteStillReadsAsNotSet() =
        runTest {
            storage.putString("llm.api_key", "garbage")
            val failingDelete =
                object : SecretKeyProvider {
                    override fun getOrCreate(alias: String) = keys.getOrCreate(alias)

                    override fun delete(alias: String) = throw IOException("keystore load failed")
                }
            val flaky = KeystoreSecretStore(suspendStorage, AesGcmSecretCipher(failingDelete, "alias"), failingDelete, "alias")

            assertNull(flaky.get("llm.api_key"))
            assertNull(storage.getStringOrNull("llm.api_key"))
        }

    @Test
    fun transientKeystoreFailureKeepsCiphertextAndKey() =
        runTest {
            store.put("llm.api_key", "sk-1")
            val before = storage.getString("llm.api_key", "")

            keys.failNextGetOrCreate = ProviderException("keystore busy")
            assertNull(store.get("llm.api_key"))

            assertEquals(before, storage.getString("llm.api_key", ""))
            assertEquals(0, keys.deleteCount)
            assertEquals("sk-1", store.get("llm.api_key"))
        }

    @Test
    fun invalidatedKeyDropsCiphertextAndKey() =
        runTest {
            store.put("llm.api_key", "sk-1")

            keys.failNextGetOrCreate = InvalidKeyException("invalidated")
            assertNull(store.get("llm.api_key"))

            assertNull(storage.getStringOrNull("llm.api_key"))
            assertEquals(1, keys.deleteCount)
        }

    @Test
    fun putRetriesOnceWithFreshKey() =
        runTest {
            keys.failNextGetOrCreate = InvalidKeyException("invalidated")

            store.put("llm.api_key", "sk-1")

            assertEquals(1, keys.deleteCount)
            assertEquals("sk-1", store.get("llm.api_key"))
        }

    @Test
    fun putFailingTwiceThrows() =
        runTest {
            val failing =
                object : SecretKeyProvider {
                    var deletes = 0

                    override fun getOrCreate(alias: String) = throw ProviderException("keystore down")

                    override fun delete(alias: String) {
                        deletes++
                    }
                }
            val broken = KeystoreSecretStore(suspendStorage, AesGcmSecretCipher(failing, "alias"), failing, "alias")

            assertFailsWith<ProviderException> { broken.put("llm.api_key", "sk-1") }
            assertEquals(1, failing.deletes)
            assertNull(storage.getStringOrNull("llm.api_key"))
        }

    @Test
    fun repositoryRoundTripKeepsKeyOutOfBothStoresAndSurvivesKeyLoss() =
        runTest {
            val prefs = MapSettings()
            val repository = StoredSettingsRepository(prefs.toSuspendSettings(), store)
            val settings = validSettings(LlmProviderType.OpenAICompatible, key = "sk-roundtrip-secret")

            repository.save(settings)

            assertEquals(settings, repository.load())
            val everyStoredValue = (prefs.keys.map { prefs.getString(it, "") } + storage.keys.map { storage.getString(it, "") })
            assertFalse(everyStoredValue.any { "sk-roundtrip-secret" in it })

            val afterKeyLoss =
                StoredSettingsRepository(
                    prefs.toSuspendSettings(),
                    KeystoreSecretStore(suspendStorage, AesGcmSecretCipher(SoftwareKeyProvider(), "alias"), keys, "alias"),
                )
            val loaded = afterKeyLoss.load()
            assertEquals(settings.copy(apiKey = ApiKey("")), loaded)
            assertEquals(setOf(SettingsFieldError.ApiKeyBlank), loaded!!.validate())
        }
}
