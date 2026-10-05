package com.tekome.vcman.data

import ai.koog.http.client.KoogHttpClient

/**
 * Single source of truth for everything that differs between LLM providers. Adding a provider means
 * adding one entry here plus one builder function in `KoogLlmChats.kt`; nothing else branches on the type.
 *
 * [id] is what gets persisted, so it must stay stable even if the enum constant is renamed.
 */
enum class LlmProviderType(
    val id: String,
    val brand: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    internal val createChat: (LlmSettings, KoogHttpClient.Factory) -> LlmChat,
) {
    OpenAICompatible(
        id = "openai",
        brand = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        createChat = ::openAiCompatibleChat,
    ),
    AnthropicCompatible(
        id = "anthropic",
        brand = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com",
        defaultModel = "claude-sonnet-4-5",
        createChat = ::anthropicCompatibleChat,
    ),
    ;

    companion object {
        /** Unknown or missing ids (e.g. after a downgrade) map to `null`, meaning "not configured". */
        fun fromId(raw: String?): LlmProviderType? = entries.firstOrNull { it.id == raw }
    }
}
