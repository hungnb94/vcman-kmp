package com.tekome.vcman.di

import android.content.Context
import com.tekome.vcman.data.SettingsRepository
import com.tekome.vcman.data.createSettingsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory

@DependencyGraph(AppScope::class)
internal interface AndroidAppGraph : AppGraph {
    @Provides
    @SingleIn(AppScope::class)
    fun provideSettingsRepository(context: Context): SettingsRepository = createSettingsRepository(context)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides context: Context,
        ): AndroidAppGraph
    }
}

fun createAppGraph(context: Context): AppGraph = createGraphFactory<AndroidAppGraph.Factory>().create(context.applicationContext)
