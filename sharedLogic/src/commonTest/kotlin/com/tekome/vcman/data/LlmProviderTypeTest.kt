package com.tekome.vcman.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LlmProviderTypeTest {
    @Test
    fun ids_areUnique() {
        assertEquals(LlmProviderType.entries.size, LlmProviderType.entries.map { it.id }.toSet().size)
    }

    @Test
    fun fromId_roundTripsEveryEntry() {
        LlmProviderType.entries.forEach { assertEquals(it, LlmProviderType.fromId(it.id)) }
    }

    @Test
    fun fromId_unknownOrMissingMeansNotConfigured() {
        assertNull(LlmProviderType.fromId("gemini"))
        assertNull(LlmProviderType.fromId(""))
        assertNull(LlmProviderType.fromId(null))
    }

    @Test
    fun specifiedDefaultBaseUrls() {
        assertEquals("https://api.openai.com/v1", LlmProviderType.OpenAICompatible.defaultBaseUrl)
        assertEquals("https://api.anthropic.com", LlmProviderType.AnthropicCompatible.defaultBaseUrl)
    }

    @Test
    fun everyEntry_hasUsableDefaults() {
        LlmProviderType.entries.forEach { type ->
            assertTrue(type.brand.isNotBlank(), type.id)
            assertTrue(type.defaultModel.isNotBlank(), type.id)
            assertTrue(validSettings(type).validate().isEmpty(), "defaults of ${type.id} must validate")
            assertFalse(isInsecureRemote(type.defaultBaseUrl), type.id)
        }
    }
}
