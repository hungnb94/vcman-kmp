package com.tekome.vcman.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tekome.vcman.domain.LanguageTag
import org.jetbrains.compose.resources.stringResource
import vcman.sharedui.generated.resources.Res
import vcman.sharedui.generated.resources.content_language_tag

/**
 * The language the LLM must answer in. Read from the `content_language_tag` string resource so it
 * follows the exact same locale fallback as the UI (device in `fr` -> UI in `en` -> answer in
 * `en`), instead of asking the platform for a locale that could disagree with the shown UI.
 */
@Composable
internal fun rememberContentLanguage(): LanguageTag {
    val raw = stringResource(Res.string.content_language_tag)
    return remember(raw) { LanguageTag.parse(raw) }
}
