package com.tekome.vcman.data

private val httpSchemes = listOf("https://", "http://")

/** Hosts that may be reached over plain http without warning the user. */
internal val localHosts: Set<String> = setOf("localhost", "127.0.0.1", "[::1]", "10.0.2.2")

/**
 * Lower-cased host of an http(s) URL, or `null` when [url] is not a usable http(s) URL.
 * Deliberately minimal (no IDN / percent-encoding) and pure commonMain; do not use it for security decisions
 * beyond validation and the insecure-transport warning.
 */
internal fun httpHostOf(url: String): String? {
    val trimmed = url.trim()
    val scheme = httpSchemes.firstOrNull { trimmed.startsWith(it, ignoreCase = true) } ?: return null
    val authority =
        trimmed
            .substring(scheme.length)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
    val host =
        if (authority.startsWith("[")) {
            if (!authority.contains(']')) return null
            authority.substringBefore(']') + "]"
        } else {
            authority.substringBefore(':')
        }
    return host.lowercase().takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) && it != "[]" }
}

/** Path part of an http(s) URL without surrounding slashes; empty when the URL has no path. */
internal fun httpPathOf(url: String): String {
    val trimmed = url.trim()
    val scheme = httpSchemes.firstOrNull { trimmed.startsWith(it, ignoreCase = true) } ?: return ""
    return trimmed
        .substring(scheme.length)
        .substringBefore('?')
        .substringBefore('#')
        .substringAfter('/', "")
        .trim('/')
}

/** True when [baseUrl] sends the API key over plain http to a host that is not local. */
fun isInsecureRemote(baseUrl: String): Boolean {
    val host = httpHostOf(baseUrl) ?: return false
    return baseUrl.trim().startsWith("http://", ignoreCase = true) && host !in localHosts
}
