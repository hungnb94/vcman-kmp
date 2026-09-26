package com.tekome.vcman.data

internal fun interface LlmChat {
    suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        tools: List<WebSearchTool>,
    ): String
}

internal fun interface LlmChatFactory {
    fun create(apiKey: ApiKey): LlmChat
}

internal typealias LlmChatResolver = (LlmProvider) -> LlmChatFactory
