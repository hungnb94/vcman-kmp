package com.tekome.vcman.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmSettingsValidationTest {
    private fun settings(
        key: String = "sk",
        url: String = "https://api.openai.com/v1",
        model: String = "m",
    ) = LlmSettings(LlmProviderType.OpenAICompatible, ApiKey(key), url, model)

    @Test
    fun everyErrorHasARule() {
        assertEquals(SettingsFieldError.entries.toSet(), settingsRules.map { it.first }.toSet())
    }

    @Test
    fun validSettingsHaveNoErrors() {
        assertTrue(settings().validate().isEmpty())
    }

    @Test
    fun reportsAllFieldErrorsAtOnce() {
        assertEquals(SettingsFieldError.entries.toSet(), settings(key = "  ", url = "ftp://x", model = "\t").validate())
    }

    @Test
    fun blankKeyVariants() {
        listOf("", "  ", "\t", "\n").forEach {
            assertEquals(setOf(SettingsFieldError.ApiKeyBlank), settings(key = it).validate(), "[$it]")
        }
    }

    @Test
    fun blankModelVariants() {
        listOf("", "  ", "\t").forEach {
            assertEquals(setOf(SettingsFieldError.ModelBlank), settings(model = it).validate(), "[$it]")
        }
    }

    @Test
    fun validUrls() {
        listOf(
            "https://api.anthropic.com",
            "http://localhost:11434/v1",
            "http://[::1]:8080",
            "HTTPS://Example.com",
            "  https://api.openai.com/v1  ",
        ).forEach { assertTrue(settings(url = it).validate().isEmpty(), it) }
    }

    @Test
    fun invalidUrls() {
        listOf("", "api.openai.com", "ftp://x", "https://", "http:// host", "https://:443", "https://[]", "javascript:alert(1)")
            .forEach { assertEquals(setOf(SettingsFieldError.BaseUrlInvalid), settings(url = it).validate(), it) }
    }

    @Test
    fun toStringNeverContainsKey() {
        assertFalse("sk-secret-777" in settings(key = "sk-secret-777").toString())
    }
}
