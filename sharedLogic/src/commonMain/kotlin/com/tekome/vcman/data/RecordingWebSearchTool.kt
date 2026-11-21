package com.tekome.vcman.data

import kotlinx.coroutines.CancellationException

/** Remembers how one analysis' searches went, whatever the agent does with the thrown errors. */
internal class RecordingWebSearchTool(
    private val delegate: WebSearchTool,
) : WebSearchTool {
    var skipped: Boolean = false
        private set

    var rejection: AnalysisException? = null
        private set

    override suspend fun search(query: String): List<WebSearchResult> =
        try {
            delegate.search(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: WebSearchUnavailableException) {
            skipped = true
            throw e
        } catch (e: AnalysisException) {
            if (e.error is AnalysisError.WebSearchRejected) rejection = e
            throw e
        }
}
