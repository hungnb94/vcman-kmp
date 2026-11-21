package com.tekome.vcman.data

import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.TimeoutCancellationException

internal sealed interface WebSearchFailure {
    /** The key itself is the problem: the user has to change it in Settings. */
    data class Rejected(
        val httpStatus: Int,
    ) : WebSearchFailure

    /** A transient or integration problem; [httpStatus] is null when no HTTP response arrived. */
    data class Unavailable(
        val httpStatus: Int?,
    ) : WebSearchFailure
}

// https://docs.firecrawl.dev/api-reference/introduction: 401 missing/invalid key, 402 out of credits.
// 403 is only documented for sources we do not request, so it is treated like a bad key.
private val rejectedStatuses = setOf(401, 402, 403)

private const val MAX_CAUSE_CHAIN_DEPTH = 8

internal fun firecrawlFailureOf(throwable: Throwable): WebSearchFailure {
    if (throwable is TimeoutCancellationException) return WebSearchFailure.Unavailable(null)
    val status =
        generateSequence(throwable) { it.cause }
            .take(MAX_CAUSE_CHAIN_DEPTH)
            .firstNotNullOfOrNull { (it as? ResponseException)?.response?.status?.value }
    return if (status != null && status in rejectedStatuses) {
        WebSearchFailure.Rejected(status)
    } else {
        WebSearchFailure.Unavailable(status)
    }
}
