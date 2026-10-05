package com.tekome.vcman.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoredSettingsRepositoryTest {
    private val prefs = MapSettings()
    private val secrets = FakeSecretStore()
    private val repository = StoredSettingsRepository(prefs, secrets)

    @Test
    fun emptyRepositoryLoadsNull() =
        runTest {
            assertNull(repository.load())
        }

    @Test
    fun roundTripsEveryProviderType() =
        runTest {
            LlmProviderType.entries.forEach { type ->
                val saved = LlmSettings(type, ApiKey("sk-${type.id}"), "https://custom.example.com/${type.id}", "model-${type.id}")

                repository.save(saved)

                assertEquals(saved, repository.load(), type.id)
            }
        }

    @Test
    fun unknownProviderIdLoadsNullWithoutCrashing() =
        runTest {
            prefs.putString("llm.provider_id", "gemini")
            secrets.put("llm.api_key", "sk")

            assertNull(repository.load())
        }

    @Test
    fun missingSecretLoadsBlankKeyButKeepsOtherFields() =
        runTest {
            val saved = validSettings(LlmProviderType.OpenAICompatible).copy(baseUrl = "http://localhost:11434", model = "llama3")
            repository.save(saved)
            secrets.values.clear()

            val loaded = repository.load()

            assertEquals(saved.copy(apiKey = ApiKey("")), loaded)
            assertTrue(loaded!!.validate().contains(SettingsFieldError.ApiKeyBlank))
        }

    @Test
    fun secretFailureLeavesNonSecretValuesUntouched() =
        runTest {
            val first = validSettings(LlmProviderType.OpenAICompatible)
            repository.save(first)
            secrets.failOnPut = true

            assertFailsWith<IllegalStateException> {
                repository.save(validSettings(LlmProviderType.AnthropicCompatible).copy(model = "other"))
            }

            secrets.failOnPut = false
            assertEquals(first, repository.load())
        }

    @Test
    fun keyIsOnlyEverWrittenToTheSecretStore() =
        runTest {
            repository.save(validSettings(key = "sk-plaintext-secret"))

            val plainValues = prefs.keys.map { prefs.getString(it, "") }
            assertFalse(plainValues.any { "sk-plaintext-secret" in it })
            assertEquals("sk-plaintext-secret", secrets.values["llm.api_key"])
        }

    @Test
    fun settingsSecretStore_delegatesToSettings() {
        val store = SettingsSecretStore(MapSettings())

        assertNull(store.get("a"))
        store.put("a", "1")
        assertEquals("1", store.get("a"))
        store.remove("a")
        assertNull(store.get("a"))
    }
}
