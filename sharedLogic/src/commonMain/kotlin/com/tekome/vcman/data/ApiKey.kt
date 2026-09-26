package com.tekome.vcman.data

import kotlin.jvm.JvmInline

@JvmInline
value class ApiKey(
    val value: String,
) {
    override fun toString(): String = "ApiKey(***)"
}
