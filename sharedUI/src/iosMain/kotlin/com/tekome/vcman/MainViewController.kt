package com.tekome.vcman

import androidx.compose.ui.window.ComposeUIViewController
import com.tekome.vcman.data.createSettingsRepository

private val settingsRepository by lazy { createSettingsRepository() }

fun MainViewController() = ComposeUIViewController { App(settingsRepository) }
