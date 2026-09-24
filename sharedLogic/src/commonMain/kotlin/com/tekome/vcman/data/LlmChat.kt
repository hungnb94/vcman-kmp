package com.tekome.vcman.data

/**
 * Minimal port the service needs from an LLM: send a system/user prompt (with the tools available
 * for this call), get the final text response back. Every Koog-specific type lives behind this
 * port's implementation in `KoogLlmChats.kt`; nothing else in this module depends on Koog.
 */
internal fun interface LlmChat {
    suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        tools: List<WebSearchTool>,
    ): String
}

/** Builds an [LlmChat] once the caller's [ApiKey] is known. */
internal fun interface LlmChatFactory {
    fun create(apiKey: ApiKey): LlmChat
}

/**
 * Resolves which [LlmChatFactory] backs a given [LlmProvider]. The default implementation
 * ([koogChatFactoryFor]) is an exhaustive `when` over the sealed [LlmProvider], so a provider
 * missing its wiring is a compile error, not a runtime failure. Injectable so tests can supply a
 * fake without touching Koog.
 */
internal typealias LlmChatResolver = (LlmProvider) -> LlmChatFactory
