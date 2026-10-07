package com.tekome.vcman

import com.tekome.vcman.data.LlmSettings
import com.tekome.vcman.data.SettingsRepository

internal class InMemorySettingsRepository(
    var settings: LlmSettings? = null,
) : SettingsRepository {
    override suspend fun load(): LlmSettings? = settings

    override suspend fun save(settings: LlmSettings) {
        this.settings = settings
    }
}
