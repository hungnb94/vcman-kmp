package com.tekome.vcman.data

/** Optional web search for the analysis; a blank key means search is off. */
data class WebSearchSettings(
    val firecrawlKey: ApiKey,
) {
    val toolConfig: WebSearchToolConfig?
        get() = firecrawlKey.value.trim().takeIf { it.isNotEmpty() }?.let { WebSearchToolConfig.Firecrawl(ApiKey(it)) }

    companion object {
        val Off = WebSearchSettings(ApiKey(""))
    }
}
