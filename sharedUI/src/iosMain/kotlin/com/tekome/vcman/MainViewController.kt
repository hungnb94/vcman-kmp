package com.tekome.vcman

import androidx.compose.ui.window.ComposeUIViewController
import com.tekome.vcman.di.createAppGraph

private val appGraph by lazy { createAppGraph() }

fun MainViewController() = ComposeUIViewController { AppRoot(appGraph.metroViewModelFactory) }
