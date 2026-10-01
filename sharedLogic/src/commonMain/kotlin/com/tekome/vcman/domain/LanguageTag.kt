package com.tekome.vcman.domain

import kotlin.jvm.JvmInline

/**
 * A BCP-47 style language tag (e.g. `en`, `vi`, `pt-BR`) naming the language the LLM must answer
 * in. The tag is embedded into a prompt, so its shape is validated: [parse] never lets free text
 * through and falls back to [Default] instead.
 *
 * Logic that receives a [LanguageTag] only forwards it; it never asks "which language is this?",
 * so supporting a new language needs no code change here.
 */
@JvmInline
value class LanguageTag private constructor(
    val value: String,
) {
    companion object {
        val Default = LanguageTag("en")

        private val SHAPE = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$")

        /** Trims [raw]; returns [Default] when it is null, blank or not tag-shaped. */
        fun parse(raw: String?): LanguageTag {
            val candidate = raw?.trim().orEmpty()
            return if (SHAPE.matches(candidate)) LanguageTag(candidate) else Default
        }
    }
}
