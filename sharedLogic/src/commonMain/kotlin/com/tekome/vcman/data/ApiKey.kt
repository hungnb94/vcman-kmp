package com.tekome.vcman.data

import kotlin.jvm.JvmInline

/**
 * Wraps a BYOK secret so it can never leak through logs, crash reports, or debug output via an
 * accidental `toString()` call or string template.
 */
@JvmInline
value class ApiKey(
    val value: String,
) {
    override fun toString(): String = "ApiKey(***)"
}
