package com.tekome.vcman.di

import androidx.lifecycle.ViewModel
import com.tekome.vcman.data.ConnectionTester
import com.tekome.vcman.data.KoogScoreAnalysisService
import com.tekome.vcman.data.LlmConnectionTester
import com.tekome.vcman.data.ScoreAnalysisService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlin.reflect.KClass

interface AppGraph : ViewModelGraph {
    @Provides
    fun provideConnectionTester(): ConnectionTester = LlmConnectionTester()

    @Provides
    fun provideScoreAnalysisService(): ScoreAnalysisService = KoogScoreAnalysisService()
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class AppViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
) : MetroViewModelFactory()
