package com.tekome.vcman.ui

internal sealed interface SetupNotice {
    data class Error(
        val message: String,
    ) : SetupNotice

    data class Clarification(
        val title: String,
        val hint: String,
        val detail: String?,
    ) : SetupNotice
}

internal fun String.asClarificationDetail(): String? = trim().takeIf { it.isNotEmpty() }
