package com.tekome.vcman.data

internal fun interface LlmChat {
    suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        tools: List<WebSearchTool>,
    ): String
}

internal typealias LlmChatResolver = (LlmSettings) -> LlmChat
