package com.tekome.vcman.di

import com.tekome.vcman.data.SettingsRepository
import com.tekome.vcman.data.createSettingsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph

@DependencyGraph(AppScope::class)
internal interface IosAppGraph : AppGraph {
    @Provides
    @SingleIn(AppScope::class)
    fun provideSettingsRepository(): SettingsRepository = createSettingsRepository()
}

fun createAppGraph(): AppGraph = createGraph<IosAppGraph>()
